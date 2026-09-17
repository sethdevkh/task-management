package com.sethdevkh.restapi.web;

public class ForbiddenException extends RuntimeException {

    public ForbiddenException() {
        super("Forbidden");
    }
}
