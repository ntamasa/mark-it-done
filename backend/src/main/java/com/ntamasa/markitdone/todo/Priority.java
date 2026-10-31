package com.ntamasa.markitdone.todo;

/** The priority of a todo. The declaration order is the sort order: URGENT comes first. */
public enum Priority {
  URGENT,
  HIGH,
  MEDIUM,
  LOW;

  @Override
  public String toString() {
    return this.name();
  }
}
