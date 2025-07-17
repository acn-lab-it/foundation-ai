package com.superagent.integrations.interfaces;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifSubIFDDirectory;

import java.io.ByteArrayInputStream;
import java.util.Date;

public class ExifExtractor {
    public static Metadata extractImageExif(byte[] buffer) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(buffer));
            ExifSubIFDDirectory directory = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
            Date date = directory != null ? directory.getDateOriginal() : null;
            System.out.println("EXIF date: " + date);
            return metadata;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
