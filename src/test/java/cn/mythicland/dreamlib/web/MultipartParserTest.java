package cn.mythicland.dreamlib.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MultipartParserTest {

    @Test
    void parsesFieldsAndUploadedBytes() {
        String body = "--x\r\n"
                + "Content-Disposition: form-data; name=mode\r\n\r\n"
                + "preview\r\n"
                + "--x\r\n"
                + "Content-Disposition: form-data; name=file; filename=items.yml\r\n"
                + "Content-Type: text/yaml\r\n\r\n"
                + "STONE:\n  Id: STONE\n"
                + "\r\n--x--\r\n";

        MultipartParser.MultipartData result = MultipartParser.parse(
                "multipart/form-data; boundary=x",
                body.getBytes(StandardCharsets.UTF_8)
        );

        assertEquals("preview", result.fields().get("mode"));
        assertEquals("items.yml", result.first("file").fileName());
        assertEquals("STONE:\n  Id: STONE\n", result.first("file").text());
    }

    @Test
    void decodesUtf8UploadFileNames() {
        String body = "--x\r\n"
                + "Content-Disposition: form-data; name=file; filename=\"其他.yml\"\r\n"
                + "Content-Type: text/yaml\r\n\r\n"
                + "STONE: {}\r\n"
                + "--x--\r\n";

        MultipartParser.MultipartData result = MultipartParser.parse(
                "multipart/form-data; boundary=x",
                body.getBytes(StandardCharsets.UTF_8)
        );

        assertEquals("其他.yml", result.first("file").fileName());
    }

    @Test
    void decodesRfc5987UploadFileNames() {
        String body = "--x\r\n"
                + "Content-Disposition: form-data; name=file; filename*=UTF-8''%E5%85%B6%E4%BB%96.yml\r\n"
                + "Content-Type: text/yaml\r\n\r\n"
                + "STONE: {}\r\n"
                + "--x--\r\n";

        MultipartParser.MultipartData result = MultipartParser.parse(
                "multipart/form-data; boundary=x",
                body.getBytes(StandardCharsets.UTF_8)
        );

        assertEquals("其他.yml", result.first("file").fileName());
    }
}
