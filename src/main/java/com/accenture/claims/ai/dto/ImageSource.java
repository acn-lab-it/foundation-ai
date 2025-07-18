package com.accenture.claims.ai.dto;

/** Holder per il data‑URI dell’immagine. */
public class ImageSource {

    private String ref;     // path locale, URL pubblico o data:image/…

    public ImageSource() {}
    public ImageSource(String ref) { this.ref = ref; }

    public String getRef()            { return ref; }
    public void   setRef(String ref)  { this.ref = ref; }
}
