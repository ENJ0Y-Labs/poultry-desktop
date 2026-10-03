package com.grantinofarms.poultry;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
@SpringBootApplication
@EnableScheduling public class PoultryApplication{public static void main(String[] args){SpringApplication.run(PoultryApplication.class,args);}}