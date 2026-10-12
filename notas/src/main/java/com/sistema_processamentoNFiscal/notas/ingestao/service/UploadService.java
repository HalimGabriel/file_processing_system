package com.sistema_processamentoNFiscal.notas.ingestao.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.UUID;

@Service
public class UploadService {

    private final S3Presigner presigner;
    private final String bucket;

    public UploadService(S3Presigner presigner, @Value("${app.bucket}") String bucket) {
        this.presigner = presigner;
        this.bucket = bucket;
    }

    public UploadUrlResponse gerarUrl(String tenantId) {
        String key = "entrada/" + tenantId + "/" + UUID.randomUUID() + ".xml";

        var request = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(10))
                .putObjectRequest(r -> r.bucket(bucket).key(key).contentType("application/xml"))
                .build();

        String url = presigner.presignPutObject(request).url().toString();

        return new UploadUrlResponse(url, key);
    }
}