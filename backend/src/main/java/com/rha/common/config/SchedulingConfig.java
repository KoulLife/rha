package com.rha.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 스케줄러(수업 자동 공개, 이용권 만료)가 공통으로 쓰므로 개별 스케줄러의 켜짐 여부와 무관하게 켠다. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
