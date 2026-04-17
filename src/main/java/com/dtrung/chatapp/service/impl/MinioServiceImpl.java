package com.dtrung.chatapp.service.impl;

import com.dtrung.chatapp.exception.BusinessException;
import com.dtrung.chatapp.service.MinioService;
import io.minio.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.Objects;

@Component
@Slf4j
@RequiredArgsConstructor
public class MinioServiceImpl implements MinioService {

    private static final String BUCKET = "resources";
    private final MinioClient minioClient;

    @PostConstruct
    private void init() {
        createBucket(BUCKET);
    }

    @SneakyThrows
    private void createBucket(final String bucketName) {
        final var found = minioClient.bucketExists(
                BucketExistsArgs.builder()
                        .bucket(bucketName)
                        .build()
        );
        if (!found) {
            minioClient.makeBucket(
                    MakeBucketArgs.builder()
                            .bucket(bucketName)
                            .build()
            );
            final var policy = """
                        {
                          "Version": "2012-10-17",
                          "Statement": [
                           {
                              "Effect": "Allow",
                              "Principal": "*",
                              "Action": "s3:GetObject",
                              "Resource": "arn:aws:s3:::%s/*"
                            }
                          ]
                        }
                    """.formatted(bucketName);
            minioClient.setBucketPolicy(
                    SetBucketPolicyArgs.builder()
                            .bucket(bucketName)
                            .config(policy)
                            .build()
            );
        }else{
            log.info("Bucket %s đã tồn tại.".formatted(bucketName));
        }
    }

    @Override
    @SneakyThrows
    public String uploadAvatar(MultipartFile image) {
        final var fileName = image.getOriginalFilename();
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(BUCKET)
                            .object(fileName)
                            .contentType(
                                    Objects.isNull(image.getContentType()) ? "image/png; image/jpg;" : image.getContentType()
                            )
                            .stream(image.getInputStream(), image.getSize(), -1)
                            .build()
            );
        }catch (final Exception e) {
            throw new BusinessException(e.getMessage());
        }
       return String.format("http://<minio-host>:<minio-port>/%s/%s", BUCKET, fileName);
    }
}
