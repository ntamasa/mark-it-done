package com.ntamasa.markitdone.user;

/**
 * Body of the request that updates the current user's preferences.
 *
 * <p>Every field is optional. A null or absent field leaves the stored value unchanged.
 *
 * @param timezone the new IANA time zone identifier, optional
 * @param themePreference the new theme preference, optional
 */
public record UpdatePreferencesRequest(
    @ValidTimezone String timezone, ThemePreference themePreference) {}
