package net.powerup.dq.service;

public record ControlResult(String code, String description, int issues, int known, String status) {}
