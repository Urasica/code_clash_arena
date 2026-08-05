package com.battle.code.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RequestDtoValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void validRestAndStompRequestsPassValidation() {
        RunRequestDto runRequest = new RunRequestDto();
        runRequest.setMatchId("123e4567-e89b-42d3-a456-426614174000");
        runRequest.setUserCode("def strategy(*args): return 'STAY'");
        runRequest.setLanguage("python");
        runRequest.setDifficulty("normal");

        assertThat(validator.validate(runRequest)).isEmpty();
        assertThat(validator.validate(new MatchQueueRequestDto("land_grab"))).isEmpty();
        assertThat(validator.validate(new GameJoinRequestDto(runRequest.getMatchId()))).isEmpty();
        assertThat(validator.validate(new GameSubmissionRequestDto(
                runRequest.getMatchId(),
                runRequest.getUserCode(),
                runRequest.getLanguage()
        ))).isEmpty();
    }

    @Test
    void invalidIdentifiersLanguagesAndGameTypesAreRejected() {
        RunRequestDto runRequest = new RunRequestDto();
        runRequest.setMatchId("../outside");
        runRequest.setUserCode(" ");
        runRequest.setLanguage("ruby");
        runRequest.setDifficulty("impossible");

        assertThat(validator.validate(runRequest)).hasSize(4);
        assertThat(validator.validate(new MatchQueueRequestDto("snake"))).hasSize(1);
        assertThat(validator.validate(new GameJoinRequestDto("not-a-uuid"))).hasSize(1);
        assertThat(validator.validate(new GameSubmissionRequestDto(
                "not-a-uuid", "", "ruby"
        ))).hasSize(3);
    }

    @Test
    void submittedCodeHasAnExplicitSizeLimit() {
        String oversizedCode = "x".repeat(RequestConstraints.MAX_CODE_LENGTH + 1);
        GameSubmissionRequestDto request = new GameSubmissionRequestDto(
                "123e4567-e89b-42d3-a456-426614174000",
                oversizedCode,
                "python"
        );

        assertThat(validator.validate(request)).hasSize(1);
    }
}
