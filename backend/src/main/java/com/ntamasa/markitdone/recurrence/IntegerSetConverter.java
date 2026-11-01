package com.ntamasa.markitdone.recurrence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** Stores a set of integers as sorted comma-separated text. Null and empty map to null. */
@Converter
public class IntegerSetConverter implements AttributeConverter<Set<Integer>, String> {

  @Override
  public String convertToDatabaseColumn(Set<Integer> attribute) {
    if (attribute == null || attribute.isEmpty()) {
      return null;
    }
    return new TreeSet<>(attribute).stream().map(String::valueOf).collect(Collectors.joining(","));
  }

  @Override
  public Set<Integer> convertToEntityAttribute(String dbData) {
    if (dbData == null || dbData.isBlank()) {
      return null;
    }
    return Arrays.stream(dbData.split(","))
        .map(String::trim)
        .map(Integer::valueOf)
        .collect(Collectors.toCollection(TreeSet::new));
  }
}
