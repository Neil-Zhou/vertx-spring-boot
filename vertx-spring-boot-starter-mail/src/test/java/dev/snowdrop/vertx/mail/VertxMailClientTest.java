package dev.snowdrop.vertx.mail;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import io.vertx.axle.core.Vertx;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class VertxMailClientTest {

    @Mock
    private io.vertx.axle.ext.mail.MailClient mockAxleMailClient;

    private Vertx vertx;

    private MailClient mailClient;

    private DataBufferFactory dataBufferFactory = new DefaultDataBufferFactory();

    @Before
    public void before() {
        io.vertx.ext.mail.MailResult vertxResult = new io.vertx.ext.mail.MailResult();
        vertxResult.setMessageID("1");
        vertxResult.setRecipients(Arrays.asList("to@example.com", "cc@example.com", "bcc@example.com"));
        given(mockAxleMailClient.sendMail(any())).willReturn(CompletableFuture.completedFuture(vertxResult));

        vertx = Vertx.vertx();

        MultiMapConverter multiMapConverter = new MultiMapConverter();
        MailAttachmentConverter mailAttachmentConverter = new MailAttachmentConverter(vertx, multiMapConverter);
        MailMessageConverter mailMessageConverter =
            new MailMessageConverter(mailAttachmentConverter, multiMapConverter);
        MailResultConverter mailResultConverter = new MailResultConverter();

        mailClient = new VertxMailClient(mockAxleMailClient, mailMessageConverter, mailResultConverter);
    }

    @Test
    public void shouldSendMessageWithBasicFields() {
        MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        headers.add("test", "example1");
        headers.add("test", "example2");

        MailMessage message = new SimpleMailMessage()
            .setFrom("from@example.com")
            .addTo("to@example.com")
            .addCc("cc@example.com")
            .addBcc("bcc@example.com")
            .setBounceAddress("bounce@example.com")
            .setSubject("Test subject")
            .setText("Test text")
            .setHtml("Test html")
            .setHeaders(headers);

        MailResult result = mailClient.send(message)
            .blockOptional()
            .orElseThrow(RuntimeException::new);

        assertThat(result.getMessageId()).isEqualTo("1");
        assertThat(result.getRecipients()).containsOnly("to@example.com", "cc@example.com", "bcc@example.com");

        ArgumentCaptor<io.vertx.ext.mail.MailMessage> vertxMessageCaptor =
            ArgumentCaptor.forClass(io.vertx.ext.mail.MailMessage.class);
        verify(mockAxleMailClient).sendMail(vertxMessageCaptor.capture());

        assertMessage(message, vertxMessageCaptor.getValue());
    }

    @Test
    public void shouldSendMessageWithAttachments() throws IOException {
        MailAttachment bufferAttachment = getAttachmentTemplate()
            .setData(Flux.just(getDataBuffer("Test buffer content 1"), getDataBuffer("Test buffer content 2")));
        MailAttachment fileAttachment = getAttachmentTemplate()
            .setFile(getFile("Test file content 1"));
        MailAttachment inlineBufferAttachment = getAttachmentTemplate()
            .setData(Flux.just(getDataBuffer("Test buffer content 3"), getDataBuffer("Test buffer content 4")));
        MailAttachment inlineFileAttachment = getAttachmentTemplate()
            .setFile(getFile("Test file content 2"));

        MailMessage message = new SimpleMailMessage()
            .addAttachment(bufferAttachment)
            .addAttachment(fileAttachment)
            .addInlineAttachment(inlineBufferAttachment)
            .addInlineAttachment(inlineFileAttachment);

        MailResult result = mailClient.send(message)
            .blockOptional()
            .orElseThrow(RuntimeException::new);

        assertThat(result.getMessageId()).isEqualTo("1");
        assertThat(result.getRecipients()).containsOnly("to@example.com", "cc@example.com", "bcc@example.com");

        ArgumentCaptor<io.vertx.ext.mail.MailMessage> vertxMessageCaptor =
            ArgumentCaptor.forClass(io.vertx.ext.mail.MailMessage.class);
        verify(mockAxleMailClient).sendMail(vertxMessageCaptor.capture());

        assertMessage(message, vertxMessageCaptor.getValue());
    }

    @Test
    public void shouldFailToSend() {
        CompletableFuture<io.vertx.ext.mail.MailResult> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("test"));

        given(mockAxleMailClient.sendMail(any())).willReturn(future);

        Mono<MailResult> result = mailClient.send(new SimpleMailMessage());
        StepVerifier.create(result)
            .expectNextCount(0)
            .expectErrorMessage("test")
            .verify();
    }

    private DataBuffer getDataBuffer(String content) {
        return dataBufferFactory.wrap(content.getBytes());
    }

    private File getFile(String content) throws IOException {
        File file = File.createTempFile("test", "tmp");
        Files.write(file.toPath(), content.getBytes(), StandardOpenOption.WRITE);

        return file;
    }

    private MailAttachment getAttachmentTemplate() {
        MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        headers.add("test", "example1");
        headers.add("test", "example2");

        return new SimpleMailAttachment()
            .setName("Test name")
            .setContentType("Test content type")
            .setDisposition("Test disposition")
            .setContentId("Test id")
            .setHeaders(headers);
    }

    private void assertMessage(MailMessage expected, io.vertx.ext.mail.MailMessage actual) {
        assertThat(actual.getFrom()).isEqualTo(expected.getFrom());
        assertThat(actual.getTo()).containsExactlyInAnyOrderElementsOf(expected.getTo());
        assertThat(actual.getCc()).containsExactlyInAnyOrderElementsOf(expected.getCc());
        assertThat(actual.getBcc()).containsExactlyInAnyOrderElementsOf(expected.getBcc());
        assertThat(actual.getBounceAddress()).isEqualTo(expected.getBounceAddress());
        assertThat(actual.getSubject()).isEqualTo(expected.getSubject());
        assertThat(actual.getText()).isEqualTo(expected.getText());
        assertThat(actual.getHtml()).isEqualTo(expected.getHtml());

        assertHeaders(expected.getHeaders(), actual.getHeaders());

        for (int i = 0; i < expected.getAttachments().size(); i++) {
            assertAttachment(expected.getAttachments().get(i), actual.getAttachment().get(i));
        }

        for (int i = 0; i < expected.getInlineAttachments().size(); i++) {
            assertAttachment(expected.getInlineAttachments().get(i), actual.getInlineAttachment().get(i));
        }
    }

    private void assertAttachment(MailAttachment expected, io.vertx.ext.mail.MailAttachment actual) {
        assertThat(actual.getName()).isEqualTo(expected.getName());
        assertThat(actual.getDescription()).isEqualTo(expected.getDescription());
        assertThat(actual.getContentId()).isEqualTo(expected.getContentId());
        assertThat(actual.getContentType()).isEqualTo(expected.getContentType());
        assertThat(actual.getDisposition()).isEqualTo(expected.getDisposition());

        assertHeaders(expected.getHeaders(), actual.getHeaders());

        if (expected.getFile() != null) {
            assertData(expected.getFile(), actual.getData());
        } else if (expected.getData() != null) {
            assertData(expected.getData(), actual.getData());
        } else {
            assertThat(actual.getData()).isNull();
        }
    }

    private void assertHeaders(MultiValueMap<String, String> expected, MultiMap actual) {
        assertThat(actual.names()).containsExactlyInAnyOrderElementsOf(expected.keySet());
        expected.forEach((k, v) -> assertThat(actual.getAll(k)).containsExactlyInAnyOrderElementsOf(v));
    }

    private void assertData(Flux<DataBuffer> expected, Buffer actual) {
        byte[] expectedBytes = expected
            .collectList()
            .map(dataBufferFactory::join)
            .map(DataBuffer::asByteBuffer)
            .map(ByteBuffer::array)
            .block();

        assertThat(actual.getBytes()).containsExactly(expectedBytes);
    }

    private void assertData(File expected, Buffer actual) {
        try {
            Buffer expectedBuffer = vertx
                .fileSystem()
                .readFile(expected.getAbsolutePath())
                .toCompletableFuture()
                .get()
                .getDelegate();

            assertThat(actual).isEqualTo(expectedBuffer);
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }
}
