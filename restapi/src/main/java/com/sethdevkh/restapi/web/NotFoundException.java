package com.sethdevkh.restapi.web;

public class NotFoundException extends RuntimeException {

    public NotFoundException() {
        super("Not found");
    }
}
