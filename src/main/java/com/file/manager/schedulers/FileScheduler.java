package com.file.manager.schedulers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.file.manager.dto.FileDto;
import com.file.manager.enums.FileType;
import com.file.manager.enums.JobStatus;
import com.file.manager.models.Job;
import com.file.manager.repositories.JobRepository;
import com.file.manager.services.ImageFileService;
import com.file.manager.services.TextPdfFileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class FileScheduler {

    @Autowired
    private SqsClient sqsClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ImageFileService imageFileService;
   @Autowired
    private TextPdfFileService textPdfFileService;

    // Replace with your SQS queue URL

    @Autowired
    private JobRepository jobRepository;

    @Value("${aws.sqs.queue-url}")
    private String queueUrl;

    // This method will run every 20 seconds
    @Scheduled(fixedDelay = 20000)
    public void pollQueue() {
        try {
            ReceiveMessageRequest request = ReceiveMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .maxNumberOfMessages(10)
                    .build();

            List<Message> messages = sqsClient.receiveMessage(request).messages();

            for (Message message : messages) {
                try {
                    // Deserialize JSON to File object
                    FileDto file = objectMapper.readValue(message.body(), FileDto.class);
                    log.info("Received file event: {}", file);

//                    Job job = Job.builder()
//                            .id(UUID.randomUUID())
//                            .jobStatus(JobStatus.PENDING)
//                            .fileType(getFileType(file.getMimeType()))
//                            .fileId(file.getId())
//                            .createdAt(LocalDateTime.now())
//                            .updatedAt(LocalDateTime.now())
//                            .build();
//
//                    jobRepository.save(job);


                    if(file.getMimeType()!=null && file.getMimeType().startsWith("image/")){
//                        job.setJobStatus(JobStatus.IN_PROGRESS);
//                        job.setUpdatedAt(LocalDateTime.now());
//                        jobRepository.save(job);
                        JobStatus status = imageFileService.handleImageFile(file);
//                        job.setJobStatus(status);
//                        job.setUpdatedAt(LocalDateTime.now());
//                        jobRepository.save(job);
                    }else if(file.getMimeType() != null &&
                            (file.getMimeType().startsWith("application/pdf") || file.getMimeType().equals("text/plain"))) {
//                        job.setJobStatus(JobStatus.IN_PROGRESS);
//                        job.setUpdatedAt(LocalDateTime.now());
//                        jobRepository.save(job);
                        JobStatus status = textPdfFileService.handleTextFile(file);
//                        job.setJobStatus(status);
//                        job.setUpdatedAt(LocalDateTime.now());
//                        jobRepository.save(job);
                    }


                    // Delete message after processing
                    sqsClient.deleteMessage(d -> d.queueUrl(queueUrl)
                            .receiptHandle(message.receiptHandle()));
                } catch (Exception e) {
                    log.error("Failed to process message: {}", message.body(), e);
                }
            }
        } catch (Exception e) {
            log.error("Error polling SQS queue", e);
        }




    }

    private FileType getFileType(String mimeType){
        if(mimeType.equalsIgnoreCase("application/pdf")){
            return FileType.PDF;

        }else if(mimeType.equalsIgnoreCase("text/plain")){
            return FileType.TXT;
        }else if(mimeType.startsWith("image/")){
            return FileType.IMAGE;
        }else{
            return FileType.OTHER;
        }
    }


}
