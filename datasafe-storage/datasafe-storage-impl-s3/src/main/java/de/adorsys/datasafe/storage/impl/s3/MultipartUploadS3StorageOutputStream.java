/*
 * Copyright 2013-2019 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * <p>
 * NOTE:
 * SimpleStorageResource.java from spring-cloud-aws
 * located by https://github.com/spring-cloud/spring-cloud-aws/blob/master/spring-cloud-aws-core/src/main/java/org/springframework/cloud/aws/core/io/s3/SimpleStorageResource.java
 * is used and was modified according Adorsys project needs.
 */

package de.adorsys.datasafe.storage.impl.s3;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;
import com.amazonaws.util.BinaryUtils;
import de.adorsys.datasafe.types.api.resource.ResourceLocation;
import de.adorsys.datasafe.types.api.utils.Obfuscate;
import lombok.SneakyThrows;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;

@Slf4j
public class MultipartUploadS3StorageOutputStream extends OutputStream {

    private String bucketName;

    private String objectName;

    private AmazonS3 amazonS3;

    // The minimum size for a multi part request is 5 MB, hence the buffer size of 5 MB
    private static final int BUFFER_SIZE = 1024 * 1024 * 5;

    private final CompletionService<UploadPartResult> completionService;

    private ByteArrayOutputStream currentOutputStream = new ByteArrayOutputStream(BUFFER_SIZE);

    private InitiateMultipartUploadResult multiPartUploadResult;

    private int partCounter = 1;

    MultipartUploadS3StorageOutputStream(String bucketName, ResourceLocation resource, AmazonS3 amazonS3,
                                                ExecutorService executorService) {
        this.bucketName = bucketName;
        this.objectName = resource.location().getPath().replaceFirst("^/", "");
        this.amazonS3 = amazonS3;
        this.completionService = new ExecutorCompletionService<>(executorService);

        log.debug("Write to bucket: {} with name: {}", Obfuscate.secure(bucketName), Obfuscate.secure(objectName));
    }

    @Override
    @Synchronized
    public void write(int b) {
        currentOutputStream.write(b);

        if (currentOutputStream.size() == BUFFER_SIZE) {
            initiateMultiPartIfNeeded();
            completionService.submit(new UploadChunkResultCallable(
                    ChunkUploadRequest
                            .builder()
                            .amazonS3(amazonS3)
                            .content(currentOutputStream.toByteArray())
                            .contentSize(currentOutputStream.size())
                            .bucketName(bucketName)
                            .objectName(objectName)
                            .uploadId(multiPartUploadResult.getUploadId())
                            .chunkNumberCounter(partCounter++)
                            .lastChunk(false)
                            .build()
            ));
            currentOutputStream.reset();
        }
    }

    @Override
    @Synchronized
    public void close() throws IOException {
        if (currentOutputStream == null) {
            return;
        }

        if (isMultiPartUpload()) {
            finishMultiPartUpload();
        } else {
            finishSimpleUpload();
        }
    }

    private boolean isMultiPartUpload() {
        return multiPartUploadResult != null;
    }

    @SneakyThrows
    private void finishSimpleUpload() {
        ObjectMetadata objectMetadata = new ObjectMetadata();
        objectMetadata.setContentLength(currentOutputStream.size());

        byte[] content = currentOutputStream.toByteArray();

        MessageDigest messageDigest = MessageDigest.getInstance("MD5");
        String md5Digest = BinaryUtils.toBase64(messageDigest.digest(content));
        objectMetadata.setContentMD5(md5Digest);

        amazonS3.putObject(
                bucketName,
                objectName,
                new ByteArrayInputStream(content),
                objectMetadata);

        // Release the memory
        currentOutputStream = null;

        log.debug("Finished simple upload");
    }

    private void finishMultiPartUpload() throws IOException {
        completionService.submit(
                new UploadChunkResultCallable(ChunkUploadRequest.builder()
                        .amazonS3(amazonS3)
                        .content(currentOutputStream.toByteArray())
                        .contentSize(currentOutputStream.size())
                        .bucketName(bucketName)
                        .objectName(objectName)
                        .uploadId(multiPartUploadResult.getUploadId())
                        .chunkNumberCounter(partCounter)
                        .lastChunk(true)
                        .build()
                )
        );

        try {
            List<PartETag> partETags = getMultiPartsUploadResults();

            log.debug("Send multipart request to S3");
            amazonS3.completeMultipartUpload(
                    new CompleteMultipartUploadRequest(
                            multiPartUploadResult.getBucketName(),
                            multiPartUploadResult.getKey(),
                            multiPartUploadResult.getUploadId(),
                            partETags
                    )
            );

            log.debug("Finished multi part upload");
        } catch (ExecutionException e) {
            abortMultiPartUpload();

            log.error(e.getMessage(), e);
            throw new IOException("Multi part upload failed ", e.getCause());
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
            abortMultiPartUpload();
            Thread.currentThread().interrupt();
        } finally {
            currentOutputStream = null;
        }
    }

    private void initiateMultiPartIfNeeded() {
        if (multiPartUploadResult == null) {

            log.debug("Initiate multi part");
            multiPartUploadResult = amazonS3
                    .initiateMultipartUpload(new InitiateMultipartUploadRequest(bucketName, objectName));
        }
    }

    private void abortMultiPartUpload() {
        log.debug("Abort multi part");

        if (isMultiPartUpload()) {
            amazonS3.abortMultipartUpload(new AbortMultipartUploadRequest(
                    multiPartUploadResult.getBucketName(),
                    multiPartUploadResult.getKey(),
                    multiPartUploadResult.getUploadId()));
        }
    }

    private List<PartETag> getMultiPartsUploadResults() throws ExecutionException, InterruptedException {
        List<PartETag> result = new ArrayList<>(partCounter);
        for (int i = 0; i < partCounter; i++) {
            UploadPartResult partResult = completionService.take().get();
            result.add(partResult.getPartETag());

            log.debug("Get upload part #{} from {}", i, partCounter);
        }
        return result;
    }

}

