package com.ntamasa.markitdone.todo.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Body of the request that marks a todo done or not done.
 *
 * @param done true for done, false for not done; required
 */
public record DoneRequest(@NotNull Boolean done) {}
