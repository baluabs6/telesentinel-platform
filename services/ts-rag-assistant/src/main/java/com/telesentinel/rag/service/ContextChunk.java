package com.telesentinel.rag.service;

/** One retrieved piece of knowledge and where it came from. */
public record ContextChunk(String source, String text) {
}
