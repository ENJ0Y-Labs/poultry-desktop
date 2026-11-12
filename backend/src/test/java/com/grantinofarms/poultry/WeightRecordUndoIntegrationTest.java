package com.grantinofarms.poultry;
import com.grantinofarms.poultry.dto.*;
import com.grantinofarms.poultry.service.*;
import com.grantinofarms.poultry.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
@SpringBootTest(properties={"spring.datasource.url=jdbc:sqlite:file:weight-undo-test?mode=memory&cache=shared"})
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class WeightRecordUndoIntegrationTest {
 @Autowired FarmService farms; @Autowired HouseService houses; @Autowired BatchService batches;
 @Autowired BroilerProductionService production; @Autowired UserRepository users; @Autowired JdbcTemplate jdbc;
 private BatchResponse batch(){farms.create(new FarmCreateRequest("Grantino Farms","Port Harcourt","Africa/Lagos","NGN"));var house=houses.create(new HouseCreateRequest("Broiler House","BH1",null));return batches.create(new BatchCreateRequest("BROILER",LocalDate.of(2026,1,1),house.id(),100,null,1000000L));}
 private String actor(){String id=UUID.randomUUID().toString();users.insert(id,"owner-"+id.substring(0,8),id+"@example.test","test-hash","Owner",Instant.now().toString());CurrentUserContext.set(id);return id;}
 @Test void undoKeepsOriginalRowAndWritesOneReversalAndAudit(){var b=batch();var w=production.addWeight(b.id(),new WeightRecordRequest(LocalDate.of(2026,1,8),10,new BigDecimal("10.000"),"Baseline"));actor();try{assertThat(production.undoWeight(b.id(),w.id(),"Incorrect sample")).containsEntry("status","VOIDED");assertThat(production.weights(b.id(),LocalDate.of(2026,1,8))).isEmpty();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM weight_records WHERE id=?",Integer.class,w.id())).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transaction_reversals WHERE original_type='WEIGHT_RECORD' AND original_id=?",Integer.class,w.id())).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE entity_type='WEIGHT_RECORD' AND entity_id=? AND action='VOID'",Integer.class,w.id())).isEqualTo(1);assertThatThrownBy(()->production.undoWeight(b.id(),w.id(),"Again")).hasMessage("This weight record has already been undone.");}finally{CurrentUserContext.clear();}}
 @Test void undoRejectsEarlierMeasurementWhenLaterOneExists(){var b=batch();var first=production.addWeight(b.id(),new WeightRecordRequest(LocalDate.of(2026,1,8),10,new BigDecimal("10.000"),"Baseline"));production.addWeight(b.id(),new WeightRecordRequest(LocalDate.of(2026,1,15),10,new BigDecimal("30.000"),"Later"));actor();try{assertThatThrownBy(()->production.undoWeight(b.id(),first.id(),"Wrong baseline")).hasMessage("This is not the latest weight record. Undo later weight records first.");assertThat(production.weights(b.id(),LocalDate.of(2026,1,15))).hasSize(2);}finally{CurrentUserContext.clear();}}
}
