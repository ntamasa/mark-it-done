package com.ntamasa.markitdone.logging;

import java.util.UUID;
import org.slf4j.MDC;

/** Helper to manage user ID in the SLF4J MDC. */
public class UserMdc {

  private static final String MDC_USER_ID_KEY = "userId";

  /**
   * Sets the user ID in the MDC for the current request.
   *
   * @param userId the user's ID
   */
  public static void set(UUID userId) {
    if (userId != null) {
      MDC.put(MDC_USER_ID_KEY, userId.toString());
    }
  }

  /** Clears the user ID from the MDC. Called at the end of the request by RequestIdFilter. */
  public static void clear() {
    MDC.remove(MDC_USER_ID_KEY);
  }
}
