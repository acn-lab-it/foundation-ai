package com.accenture.claims.ai.adapter.inbound.rest.dto;
import jakarta.ws.rs.FormParam;
import org.jboss.resteasy.reactive.PartType;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.util.List;

public class ChatForm {

    @FormParam("userMessage")
    public String userMessage;

    /** 0‑n file di qualunque tipo (immagini, video, pdf…). */
    @FormParam("files")
    @PartType("application/octet-stream")
    public List<FileUpload> files;
}
