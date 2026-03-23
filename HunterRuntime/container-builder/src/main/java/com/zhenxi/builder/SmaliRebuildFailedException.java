package com.zhenxi.builder;

public class SmaliRebuildFailedException extends RuntimeException {
    public SmaliRebuildFailedException(Exception e) {
        super(e);
    }
}
