package com.accenture.claims.ai.port;

import com.accenture.claims.ai.adapter.inbound.rest.dto.email.DownloadedAttachment;
import com.accenture.claims.ai.adapter.inbound.rest.dto.email.EmailDto;

import java.util.Map;

public interface EmailService {

    /**
     * Finds and retrieves an email by its unique identifier.
     *
     * @param id the unique identifier of the email to retrieve
     * @return an {@link EmailDto} object containing the details of the email
     * @throws Exception if an error occurs during the operation, such as issues with the HTTP request or response
     */
    EmailDto findOne(String id) throws Exception;

    /**
            * Retrieves a paginated collection of email data from the API.
            *
            * @param page the current page number to retrieve
     * @param size the number of items per page to retrieve
     * @return a map containing the results of the retrieval, representing a page of email data
     * @throws Exception if an error occurs during the operation, such as issues with the HTTP request or parsing the response
     */
    Map<String, Object> findAll(int page, int size) throws Exception;


    /**
     * Downloads an email attachment by its file name and email ID.
     *
     * @param emailId  the unique identifier of the email containing the attachment
     * @param filename the name of the attachment file to be downloaded
     * @return a {@link DownloadedAttachment} object containing the downloaded content, its content type,
     *         and content disposition
     * @throws Exception if an error occurs during the download process, such as HTTP request failures or invalid responses
     */
    DownloadedAttachment downloadAttachment(String emailId, String filename) throws Exception;

}
