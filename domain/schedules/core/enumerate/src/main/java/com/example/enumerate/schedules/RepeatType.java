package com.example.enumerate.schedules;

import lombok.Getter;

@Getter
public enum RepeatType {
    NONE, DAILY, WEEKLY, MONTHLY;

    public static RepeatType from(String value) {
        if (value == null || value.equalsIgnoreCase("null")) {
            return NONE;
        }

        try {
            return RepeatType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return NONE;
        }
    }
}
