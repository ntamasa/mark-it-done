package com.ntamasa.markitdone.recurrence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IntegerSetConverterTest {

  private final IntegerSetConverter converter = new IntegerSetConverter();

  @Test
  @DisplayName("a set round-trips as sorted comma-separated integers")
  void roundTrip() {
    String db = converter.convertToDatabaseColumn(Set.of(15, 1, 31));

    assertThat(db).isEqualTo("1,15,31");
    assertThat(converter.convertToEntityAttribute(db)).isEqualTo(new TreeSet<>(Set.of(1, 15, 31)));
  }

  @Test
  @DisplayName("null and empty map to null")
  void nullAndEmpty() {
    assertThat(converter.convertToDatabaseColumn(null)).isNull();
    assertThat(converter.convertToDatabaseColumn(Set.of())).isNull();
    assertThat(converter.convertToEntityAttribute(null)).isNull();
    assertThat(converter.convertToEntityAttribute("")).isNull();
  }
}
