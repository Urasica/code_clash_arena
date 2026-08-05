package com.battle.code.dto;

public final class RequestConstraints {

    public static final String MATCH_ID_PATTERN =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$";
    public static final String LANGUAGE_PATTERN = "^(python|java|c|cpp|javascript)$";
    public static final String DIFFICULTY_PATTERN = "^(easy|normal|hard)$";
    public static final String GAME_TYPE_PATTERN = "^land_grab$";
    public static final int MAX_CODE_LENGTH = 64_000;

    private RequestConstraints() {
    }
}
