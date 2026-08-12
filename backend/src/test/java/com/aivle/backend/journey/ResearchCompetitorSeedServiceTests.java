package com.aivle.backend.journey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aivle.backend.common.exception.BusinessException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 경쟁 씨앗 — <b>「비었다」의 정의를 한 곳에 두는지</b>를 잰다.
 *
 * <p>층마다 다르면 화면은 「썼다」고 하고 하네스는 「없다」고 읽는다. 그리고 그 차이가
 * 조용히 값을 바꾼다 — 씨앗이 있으면 하네스가 {@code corp_name} 을 요구하고, 없으면
 * 요구를 끈다({@code slot_harness._rule19}). <b>빈 블록은 가장 나쁜 상태다.</b>
 */
class ResearchCompetitorSeedServiceTests {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ResearchCompetitorSeedRepository repository =
        mock(ResearchCompetitorSeedRepository.class);
    private final ResearchCompetitorSeedService service =
        new ResearchCompetitorSeedService(repository, MAPPER);

    private final List<ResearchCompetitorSeed> stored = new ArrayList<>();

    @BeforeEach
    void wire() {
        when(repository.findAllByProjectIdAndDeletedAtIsNullOrderByDisplayOrderAsc(anyLong()))
            .thenReturn(stored);
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    private static JsonNode json(String text) {
        return MAPPER.readTree(text);
    }

    @Test
    @DisplayName("씨앗 0개는 막지 않는다 — 경고만 돌려준다")
    void anEmptySeedListIsWarnedNotRefused() {
        ResearchCompetitorSeedService.SeedsView view = service.replace(7L, 1L, json("[]"));

        assertThat(view.seeds()).isEmpty();
        assertThat(view.warning()).contains("경쟁 씨앗이 없다");
    }

    @Test
    @DisplayName("씨앗이 없으면 컨셉 블록 자체를 만들지 않는다 — 빈 블록은 corp_name 을 요구시킨다")
    void noSeedsMeansNoConceptBlock() {
        assertThat(service.conceptBlock(7L)).isNull();
    }

    @Test
    @DisplayName("컨셉 블록의 키는 이름·왜·운영사다 — 하네스가 그대로 읽는다")
    void conceptBlockUsesTheKeysTheHarnessReads() {
        stored.add(ResearchCompetitorSeed.create("s1", 7L, 1, "공비서",
            "예약금 기반 노쇼 차단", null, 1L));
        stored.add(ResearchCompetitorSeed.create("s2", 7L, 2, "핸드SOS",
            "가격 관측 겸용", "예스오예스", 1L));

        JsonNode block = service.conceptBlock(7L);

        assertThat(block.get("seeds")).hasSize(2);
        assertThat(block.at("/seeds/0/이름").stringValue()).isEqualTo("공비서");
        assertThat(block.at("/seeds/0/왜").stringValue()).isEqualTo("예약금 기반 노쇼 차단");
        // 비상장이면 null 이다 — 빈 문자열로 두면 DART 를 빈 법인명으로 친다.
        assertThat(block.at("/seeds/0/운영사").isNull()).isTrue();
        assertThat(block.at("/seeds/1/운영사").stringValue()).isEqualTo("예스오예스");
    }

    @Test
    @DisplayName("이름만 있고 왜가 없으면 거부한다 — 하네스 프롬프트가 둘을 같이 읽는다")
    void bothNameAndReasonAreRequired() {
        assertThatThrownBy(() -> service.replace(7L, 1L,
            json("[{\"name\":\"공비서\",\"reason\":\"  \"}]")))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("빈 줄은 칸을 만들지 않는다 — 화면의 빈 입력란이 씨앗이 되면 안 된다")
    void blankRowsAreDropped() {
        ResearchCompetitorSeedService.SeedsView view = service.replace(7L, 1L,
            json("[{\"name\":\"  \",\"reason\":\"\"},{\"name\":\"공비서\",\"reason\":\"직접 경쟁\"}]"));

        assertThat(view.seeds()).hasSize(1);
        assertThat(view.seeds().get(0).name()).isEqualTo("공비서");
        assertThat(view.seeds().get(0).displayOrder()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 경쟁을 두 번 적으면 거부한다 — 같은 subject 가 두 번 실리면 슬롯이 갈린다")
    void duplicateNamesAreRefused() {
        assertThatThrownBy(() -> service.replace(7L, 1L, json(
            "[{\"name\":\"공비서\",\"reason\":\"a\"},{\"name\":\"공비서\",\"reason\":\"b\"}]")))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("사용자가 적은 차례를 보존한다 — 순서가 곧 중요도다")
    void orderIsPreserved() {
        ResearchCompetitorSeedService.SeedsView view = service.replace(7L, 1L, json(
            "[{\"name\":\"가\",\"reason\":\"1\"},{\"name\":\"나\",\"reason\":\"2\"},"
            + "{\"name\":\"다\",\"reason\":\"3\"}]"));

        assertThat(view.seeds()).extracting(ResearchCompetitorSeedService.SeedView::name)
            .containsExactly("가", "나", "다");
        assertThat(view.seeds()).extracting(ResearchCompetitorSeedService.SeedView::displayOrder)
            .containsExactly(1, 2, 3);
    }
}
