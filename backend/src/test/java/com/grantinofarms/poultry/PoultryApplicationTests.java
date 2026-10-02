package com.grantinofarms.poultry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
@SpringBootTest(properties={"spring.datasource.url=jdbc:sqlite:file:testdb?mode=memory&cache=shared","poultry.database-path=:memory:"})
class PoultryApplicationTests{@Test void contextLoads(){}}