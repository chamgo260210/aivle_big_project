package com.aivle.backend.pipeline.refinement;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.journey.MarketResearchRun;
import com.aivle.backend.journey.MarketResearchVersion;
import com.aivle.backend.journey.MarketResearchVersionRepository;
import com.aivle.backend.pipeline.conceptportfolio.selection.application.ConceptPortfolioSelectionTaskFactory;
import com.aivle.backend.pipeline.conceptportfolio.selection.domain.ConceptPortfolioSelection;
import com.aivle.backend.pipeline.conceptportfolio.selection.repository.ConceptPortfolioSelectionRepository;
import com.aivle.backend.taskrun.contract.ConceptDriftContract;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.domain.TaskRunState;
import com.aivle.backend.taskrun.repository.TaskRunRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * 컨셉 다듬기 루프의 <b>한 걸음</b>을 만든다. 루프를 도는 것은 {@link ConceptRefinementWorker} 다.
 *
 * <p>왜 Spring 이 도나. 라운드마다 법률(DELTA_LEGAL)이 따라붙고, 법률은 이미 Spring 이
 * 소유한 상태 기계다. AI 쪽에서 돌리면 상태 소유자가 둘이 된다.
 *
 * <p>⚠ <b>라운드마다 BM 을 다시 돌리지 않는다.</b> 수렴한 뒤 한 번만 재검증한다 —
 * 라운드마다 돌리면 20분짜리를 세 번 태운다.
 */
@Service
public class ConceptRefinementService {

    private final ConceptPortfolioSelectionRepository selections;
    private final ConceptRefinementRoundRepository rounds;
    private final MarketResearchVersionRepository researchVersions;
    private final ConceptPortfolioSelectionTaskFactory tasks;
    private final com.aivle.backend.pipeline.conceptportfolio.selection.repository
        .ConceptPortfolioDeltaLegalReviewRepository deltas;
    private final com.aivle.backend.pipeline.conceptportfolio.selection.repository
        .ConceptLegalRegulatoryReportRepository reports;
    private final ConceptRefinementFinalRepository finals;
    /** 입력 뼈대(seed·selectedCandidate·baseLegalReview)를 만드는 쪽. 손으로 짓지 않는다. */
    private final com.aivle.backend.pipeline.conceptportfolio.selection.application
        .ConceptPortfolioSelectionService selectionService;
    /** 실패한 시도를 되짚기 위해서만 본다 — 실행을 «만드는» 것은 여전히 {@link #tasks} 다. */
    private final TaskRunRepository taskRuns;
    /** 사람이 고른 것을 실제로 컨셉에 얹는 쪽. {@link #decide} 만 부른다. */
    private final ConceptRefinementApplyService applyService;
    private final ObjectMapper mapper;

    public ConceptRefinementService(ConceptPortfolioSelectionRepository selections,
            ConceptRefinementRoundRepository rounds, MarketResearchVersionRepository researchVersions,
            ConceptPortfolioSelectionTaskFactory tasks,
            com.aivle.backend.pipeline.conceptportfolio.selection.repository
                .ConceptPortfolioDeltaLegalReviewRepository deltas,
            com.aivle.backend.pipeline.conceptportfolio.selection.repository
                .ConceptLegalRegulatoryReportRepository reports,
            ConceptRefinementFinalRepository finals,
            com.aivle.backend.pipeline.conceptportfolio.selection.application
                .ConceptPortfolioSelectionService selectionService,
            TaskRunRepository taskRuns, ConceptRefinementApplyService applyService,
            ObjectMapper mapper) {
        this.selections = selections;
        this.rounds = rounds;
        this.researchVersions = researchVersions;
        this.tasks = tasks;
        this.deltas = deltas;
        this.reports = reports;
        this.finals = finals;
        this.selectionService = selectionService;
        this.taskRuns = taskRuns;
        this.applyService = applyService;
        this.mapper = mapper;
    }

    /**
     * <b>실패한 라운드를 사용자가 다시 건다.</b> (2026-08-14 신설)
     *
     * <p>왜 필요했나. 멱등키가 라운드로 고정({@code refine:<selectionId>:<round>})이라
     * {@link com.aivle.backend.taskrun.service.TaskRunService#createWithDisposition}이 <b>상태를
     * 보지 않고</b> 기존 실행을 그대로 돌려준다 — FAILED 여도 그렇다. 그래서 한 번 실패한 라운드는
     * 영영 다시 걸 수 없었고, 라운드 행이 아직 없으니({@code ConceptPortfolioSelection
     * MaterializationService} 가 채택 성공 때 만든다) 폴러가 볼 대상도 없었다.
     * 화면은 그것을 <b>「아직 안 함」(NOT_STARTED)</b> 으로 보였다 — 조용한 거짓말이다.
     *
     * <p>고치는 방법은 멱등을 푸는 것이 아니라 <b>키를 하나 더 만드는 것</b>이다.
     * {@code refine:<selectionId>:<round>:r2} · {@code :r3} 로 이어 붙인다. 멱등 자체는 그대로라
     * 폴러가 두 번 깨어나도 같은 시도가 두 번 서지 않는다.
     *
     * <p><b>사용자가 눌러야만 돈다.</b> 자동 재시도는 일부러 안 넣었다 — 유료 호출이
     * 사용자 의도 없이 반복되면 되짚을 수 없다.
     *
     * @throws com.aivle.backend.common.exception.BusinessException 돌고 있거나, 이미 됐거나, 상한을 넘었을 때
     */
    @Transactional
    public TaskRun retryRound(Long ownerId, Long projectId, Long selectionId) {
        ConceptPortfolioSelection selection = selections.findById(selectionId)
            .filter(value -> value.getProjectId().equals(projectId))
            .orElseThrow(() -> new BusinessException(ErrorCode.CONCEPT_NOT_SELECTABLE));
        var last = rounds.findTopBySelectionIdAndDeletedAtIsNullOrderByRoundDesc(selectionId);

        // ★ **조사가 그 사이 새로 돌았으면 「다른 제안」은 새 주기다.** 옛 주기를 이어가면
        //    사용자는 「다른 제안」을 눌렀는데 **낡은 조사 기준**의 제안을 받는다 — 그리고
        //    옛 주기가 상한 3을 이미 썼으면 아예 눌러지지도 않는다(실측: 그 상태였다).
        Integer now = currentResearchVersion(projectId);
        if (last.isPresent() && now != null
            && !java.util.Objects.equals(last.get().getResearchVersion(), now)) {
            supersede(selectionId);
            return queueNextRound(selection.getSelectedByUserId(), projectId, selectionId,
                roundKey(selectionId, now, 1));
        }

        int round = last.map(value -> value.getRound() + 1).orElse(1);
        // 재시도는 **같은 주기 안에서** 다음 라운드를 거는 것이다 — 조사판도 그 주기의 것을 쓴다.
        Integer version = last.map(ConceptRefinementRound::getResearchVersion).orElse(now);
        if (round > ConceptRefinementRound.MAX_ROUNDS) {
            throw new BusinessException(ErrorCode.JOB_RETRY_NOT_ALLOWED);
        }
        List<TaskRun> attempts = attemptsOf(projectId, selectionId, version, round);
        // 아직 살아 있는 시도가 있으면 두 번 걸지 않는다. 「실패했나」를 눈으로 보고 누르는
        // 버튼이라 사용자가 성급히 두 번 누를 수 있다.
        if (attempts.stream().anyMatch(run -> !isTerminal(run.getState()))) {
            throw new BusinessException(ErrorCode.JOB_RETRY_NOT_ALLOWED);
        }
        if (attempts.stream().anyMatch(run -> run.getState() == TaskRunState.SUCCEEDED)) {
            throw new BusinessException(ErrorCode.JOB_RETRY_NOT_ALLOWED);
        }
        if (attempts.size() >= MAX_ROUND_ATTEMPTS) {
            throw new BusinessException(ErrorCode.JOB_RETRY_NOT_ALLOWED);
        }
        String key = attemptKey(selectionId, version, round, attempts.size() + 1);
        return queueNextRound(selection.getSelectedByUserId(), projectId, selectionId, key);
    }

    /** 한 라운드에 지금까지 선 시도들. 첫 시도와 재시도가 <b>같은 목록</b>에 들어온다. */
    @Transactional(readOnly = true)
    public List<TaskRun> attemptsOf(Long projectId, Long selectionId, Integer version, int round) {
        return taskRuns.findByProjectIdAndIdempotencyScopeAndIdempotencyKeyStartingWithAndDeletedAtIsNullOrderByCreatedAtAsc(
            projectId,
            com.aivle.backend.taskrun.service.TaskRunService.idempotencyScope(
                ConceptPortfolioSelectionTaskFactory.TYPE,
                "CONCEPT_PORTFOLIO_SELECTION", selectionId.toString()),
            roundKey(selectionId, version, round));
    }

    /**
     * 첫 시도의 키는 <b>예전 그대로 둔다</b>({@code refine:<sel>:<round>}).
     *
     * <p>뒤에 {@code :r1} 을 붙이고 싶겠지만 그러면 이미 DB 에 있는 라운드들의 키와 갈려
     * 옛 실행이 목록에서 사라진다 — 「몇 번 시도했나」가 조용히 틀린다.
     *
     * <h4>⚠⚠ 조사판이 키에 들어가는 이유 — 안 넣으면 새 주기가 «조용히» 안 선다</h4>
     * 새 조사판이 오면 라운드 번호를 1부터 다시 센다. 그런데 키가 {@code refine:<sel>:1} 하나뿐이면
     * 옛 주기의 라운드 1과 <b>같은 키</b>가 된다 → {@code TaskRunService.createWithDisposition} 이
     * 멱등 재생으로 <b>옛 TaskRun 을 돌려주고</b>, {@code createdNew()} 가 거짓이라
     * 팩토리가 「replay authority mismatch」를 던진다. 그 예외는 {@code startRefinementLoop} 이
     * 로그로만 삼키므로 <b>사용자에게는 아무 일도 안 일어난 것처럼 보인다</b>
     * (2026-08-16 실측 — V31 을 넣고 이 자리를 안 고쳤다면 그대로 죽은 기능이었다).
     *
     * <p>⚠ {@code version} 이 {@code null} 이면 <b>옛 모양 그대로</b>다. V31 이전 라운드들의
     * 키가 갈리면 위 주석이 말하는 「몇 번 시도했나」가 바로 틀어진다.
     */
    static String roundKey(Long selectionId, Integer version, int round) {
        return version == null ? "refine:" + selectionId + ":" + round
            : "refine:" + selectionId + ":v" + version + ":" + round;
    }

    /**
     * 사람의 결정이 <b>가설 확정 실행</b>으로 이어질 때 쓰는 멱등키.
     *
     * <p>화면이 주는 열쇠에 조사판을 덧붙인다 — 화면은 조사판을 모르고, 라운드 번호는
     * 새 주기마다 1부터 다시 세므로 <b>주기가 다른 두 결정이 같은 열쇠를 갖는다.</b>
     *
     * <p>⚠ 조사판을 모르는 옛 라운드는 <b>그대로</b>다. 바꾸면 이미 선 실행과 갈린다.
     */
    static String applyKey(String clientKey, Integer version) {
        return version == null ? clientKey : clientKey + ":v" + version;
    }

    static String attemptKey(Long selectionId, Integer version, int round, int attempt) {
        return attempt <= 1 ? roundKey(selectionId, version, round)
            : roundKey(selectionId, version, round) + ":r" + attempt;
    }

    static boolean isTerminal(TaskRunState state) {
        return state == TaskRunState.SUCCEEDED || state == TaskRunState.FAILED
            || state == TaskRunState.CANCELLED || state == TaskRunState.TIMED_OUT;
    }

    /** 한 라운드에 허용하는 시도 횟수. 첫 시도 + 재시도 2회. */
    public static final int MAX_ROUND_ATTEMPTS = 3;

    /**
     * 화면이 「실패했고 다시 걸 수 있다」를 그릴 재료.
     *
     * <p>⚠ <b>이것이 없으면 실패가 「아직 안 함」으로 보인다.</b> 라운드 행은 채택 성공 때만
     * 생기므로, 라운드가 0개인 상태는 <b>「시작 전」과 「1라운드가 실패함」이 구별되지 않는다</b>.
     * 실행 쪽을 봐야만 갈린다.
     *
     * <p>{@code failed} 가 참인데 {@code retryable} 이 거짓이면 상한을 다 쓴 것이다 —
     * 화면은 버튼 대신 사유를 보인다.
     */
    @Transactional(readOnly = true)
    public RetryStatus retryStatus(Long projectId, Long selectionId) {
        var previous = rounds.findTopBySelectionIdAndDeletedAtIsNullOrderByRoundDesc(selectionId);
        int round = previous.map(value -> value.getRound() + 1).orElse(1);
        Integer version = previous.map(ConceptRefinementRound::getResearchVersion)
            .orElseGet(() -> currentResearchVersion(projectId));
        if (round > ConceptRefinementRound.MAX_ROUNDS) {
            return new RetryStatus(false, false, 0, MAX_ROUND_ATTEMPTS, null);
        }
        List<TaskRun> attempts = attemptsOf(projectId, selectionId, version, round);
        if (attempts.isEmpty()) return new RetryStatus(false, false, 0, MAX_ROUND_ATTEMPTS, null);
        TaskRun last = attempts.get(attempts.size() - 1);
        boolean running = attempts.stream().anyMatch(run -> !isTerminal(run.getState()));
        boolean succeeded = attempts.stream().anyMatch(run -> run.getState() == TaskRunState.SUCCEEDED);
        boolean failed = !running && !succeeded;
        return new RetryStatus(failed, failed && attempts.size() < MAX_ROUND_ATTEMPTS,
            attempts.size(), MAX_ROUND_ATTEMPTS, failed ? last.getLastErrorReason() : null);
    }

    /**
     * @param failed        마지막 시도가 끝났는데 성공하지 못했다
     * @param retryable     아직 시도를 더 쓸 수 있다 — 화면이 버튼을 낸다
     * @param attemptsUsed  이 라운드에 지금까지 선 시도 수(첫 시도 포함)
     * @param reason        마지막 실패 사유. <b>없을 수 있다</b> — 그때 화면이 지어내지 않는다
     */
    public record RetryStatus(boolean failed, boolean retryable, int attemptsUsed,
                              int maxAttempts, String reason) { }

    /**
     * 다듬기가 더 돌 수 있는가. 상한(3)과 「고칠 것 없음」이 여기서 갈린다.
     *
     * <p>⚠ <b>2026-08-15 정정.</b> 「통과분이 하나도 없었으면」을 <b>제안 유무</b>로만 재고
     * 있었다. 사람이 고르는 문이 생긴 뒤로는 그 말의 뜻이 <b>「채택분이 없으면」</b>이어야 한다 —
     * 안 그러면 사용자가 <b>전부 거절했는데도</b> 「아직 고칠 게 남았다」로 읽어 다음 라운드를
     * 건다. 거절은 「더 해 보라」가 아니라 「그만」이다.
     */
    @Transactional(readOnly = true)
    public boolean canRunAnotherRound(Long selectionId) {
        Optional<ConceptRefinementRound> last = rounds
            .findTopBySelectionIdAndDeletedAtIsNullOrderByRoundDesc(selectionId);
        if (last.isEmpty()) return true;
        if (last.get().getRound() >= ConceptRefinementRound.MAX_ROUNDS) return false;
        if (proposalsOf(last.get()).isEmpty()) return false;
        // 결정을 안 한 라운드는 사람 앞에 멈춰 있는 것이므로 여기서 다음을 걸지 않는다.
        return !acceptedOf(last.get()).isEmpty();
    }

    /**
     * 사람이 고른 칸. 아직 안 골랐으면 <b>빈 집합</b>이다.
     *
     * <p>⚠ 「아직 안 골랐다」와 「전부 넘겼다」를 여기서 뭉갠다 — 둘 다 <b>적용된 것이 없다</b>는
     * 점에서 같기 때문이다. 그 둘을 <b>가르는</b> 것은 화면 결말({@code outcomeOf})의 일이고,
     * 그쪽은 {@code getAcceptedFieldsJson() == null} 을 직접 본다.
     */
    @Transactional(readOnly = true)
    public java.util.Set<String> acceptedOf(ConceptRefinementRound round) {
        if (round.getAcceptedFieldsJson() == null) return java.util.Set.of();
        java.util.Set<String> keys = new java.util.LinkedHashSet<>();
        for (JsonNode node : mapper.readTree(round.getAcceptedFieldsJson())) keys.add(node.asText());
        return keys;
    }

    /**
     * <b>사람이 고른 것만 적용한다.</b> 이 판이 세우는 정의 그 자체다.
     *
     * <p>지금까지는 AI 결과가 채택되는 순간 전량이 자동 적용됐다. 그 자리를 떼고 이 문을 만든다.
     * 라운드는 제안을 담은 채 {@code legalOutcome == null} 로 <b>사람 앞에 멈춰</b> 있고
     * (워커는 닫힌 라운드만 본다), 사용자가 체크한 칸만 여기서 적용된다.
     *
     * <p>라운드를 닫는 길은 넷이다:
     * <ol>
     *   <li>가설 칸을 골랐다 → {@code confirm()} 이 법률을 다시 태우고 그 결과가 닫는다</li>
     *   <li>오버레이·BM 칸만 골랐다 → 다시 볼 법이 없으니 <b>여기서</b> 닫는다</li>
     *   <li>고른 값이 의미 검사에 걸렸다 → {@code CONFIRM_HYPOTHESES} 갈래가 닫는다</li>
     *   <li>전부 거절했다 → <b>닫지 않는다.</b> 컨셉은 그대로고 루프는 여기서 끝난다</li>
     * </ol>
     *
     * @param fieldKeys 사용자가 체크한 칸. 빈 목록이면 「전부 넘김」이다(null 과 다르다).
     */
    @Transactional
    public void decide(Long ownerId, Long projectId, Long selectionId, int round,
            List<String> fieldKeys, String idempotencyKey) {
        ConceptRefinementRound target = rounds
            .findBySelectionIdAndDeletedAtIsNullOrderByRoundAsc(selectionId).stream()
            .filter(value -> value.getRound() == round)
            .findFirst()
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        // 이미 닫힌 라운드는 결정을 받지 않는다 — 법률까지 지난 것을 되돌리는 문은 없다.
        if (target.getLegalOutcome() != null) throw new BusinessException(ErrorCode.MODULE_INPUT_STALE);
        java.util.Set<String> picked = new java.util.LinkedHashSet<>(
            fieldKeys == null ? List.of() : fieldKeys);
        ArrayNode chosen = mapper.createArrayNode();
        ArrayNode accepted = mapper.createArrayNode();
        boolean hypothesisPicked = false;
        for (JsonNode proposal : proposalsOf(target)) {
            String field = proposal.path("fieldKey").asText();
            if (!picked.contains(field)) continue;
            chosen.add(proposal);
            accepted.add(field);
            if (ConceptRefinementApplyService.isHypothesisField(field)) hypothesisPicked = true;
        }
        // ⚠ **결정을 먼저 적는다.** 두 번 눌러도 두 번 적용되지 않게 하는 자물쇠가 이것이다.
        if (!target.recordDecision(mapper.writeValueAsString(accepted))) {
            throw new BusinessException(ErrorCode.MODULE_INPUT_STALE);
        }
        if (chosen.isEmpty()) return;   // 전부 거절 — 컨셉은 그대로 두고 루프를 끝낸다
        // ⚠⚠ **조사판을 열쇠에 붙인다.** 화면이 주는 열쇠는 `refine-decide-<선택>-<라운드>` 인데
        //    새 주기가 오면 라운드가 다시 1부터라 **옛 주기의 결정과 열쇠가 겹친다.** 고른 칸이
        //    다르면 입력 해시가 달라 `IDEMPOTENCY_CONFLICT` 로 거절되고, 사용자에게는
        //    「요청을 완료하지 못했습니다」만 뜬다(2026-08-16 실측 — 새 주기 첫 반영이 통째로 막혔다).
        //    화면은 조사판을 모른다. 아는 쪽이 붙이는 것이 맞다.
        //    ⚠ 옛 라운드(조사판 모름)는 열쇠를 **그대로** 둔다 — 바꾸면 이미 선 실행과 갈린다.
        applyService.apply(ownerId, projectId, selectionId, chosen,
            applyKey(idempotencyKey, target.getResearchVersion()));
        if (!hypothesisPicked) {
            // 다시 볼 법이 없다. 안 닫으면 라운드가 영영 열려 화면이 「고를 차례」에 갇힌다.
            target.recordLegal(ConceptRefinementRound.LegalOutcome.PASSED, "[]");
        }
    }

    /**
     * <b>루프의 첫 문.</b> 사업 검증(VALIDATION)이 성공해 결과가 채택된 직후 라운드 1을 건다.
     *
     * <p>왜 여기인가. {@link ConceptRefinementWorker#advance}는 <b>이미 있는 라운드</b>만 다음으로
     * 민다({@code findByLegalOutcomeIsNotNull...}). 그래서 라운드 1을 거는 자리가 어디에도 없었고
     * 다듬기는 영원히 시작되지 않았다(2026-08-13 실측: {@code concept_refinement_rounds} 0행,
     * 사업 검증이 SUCCEEDED 인데도 화면은 「아직 안 함」).
     *
     * <p>루프의 재료는 시장조사·BM 판정이다({@link #material}). 그러니 <b>그것이 막 생긴 순간</b>이
     * 유일하게 정확한 시점이다 — 검증 결과 없이 라운드를 걸면 모델이 근거 없이 컨셉을 고친다.
     *
     * <p><b>정확히 한 번만 건다.</b> 이 선택에 라운드가 하나라도 있으면 조용히 건너뛴다.
     * 폴러가 다시 눌러도, 사용자가 검증을 다시 돌려도 라운드 1이 두 번 서지 않는다.
     * 그 뒤의 라운드는 {@link ConceptRefinementWorker} 몫이다 — 멱등키 모양을 그쪽과 맞춰 둔다.
     *
     * <p>⚠ <b>일부러 {@code @Transactional} 을 안 붙였다.</b> 호출자(시장조사 채택)의 트랜잭션에
     * 그대로 얹혀야 한다 — 방금 만든 {@code MarketResearchVersion} 이 아직 커밋 전이라
     * {@code REQUIRES_NEW} 로 떼면 {@link #material} 이 <b>옛 검증 결과를 읽는다</b>.
     * 그렇다고 붙여서 참여시키면, 여기서 던진 예외가 트랜잭션을 rollback-only 로 물들여
     * 호출자가 try/catch 로 삼켜도 <b>시장조사 채택이 통째로 되돌아간다</b>.
     * 애너테이션이 없으면 인터셉터가 안 끼어 그 표시가 안 붙는다.
     *
     * @return 새로 건 라운드의 TaskRun. 걸 이유가 없었으면 비어 있다.
     */
    public Optional<TaskRun> startFirstRound(Long projectId) {
        ConceptPortfolioSelection selection = selections
            .findByProjectIdAndIsCurrentTrueAndDeletedAtIsNull(projectId).orElse(null);
        if (selection == null) return Optional.empty();

        Integer now = currentResearchVersion(projectId);
        var last = rounds.findTopBySelectionIdAndDeletedAtIsNullOrderByRoundDesc(selection.getId());
        if (last.isPresent()) {
            // 조사판 번호를 모르면 「같은 판인가」를 물을 수 없다 — 그때는 **옛 규칙(한 번만)**
            // 으로 물러난다. 여기서 새 주기를 열면 폴링마다 라운드가 서고 유료 호출이 반복된다.
            if (now == null) return Optional.empty();
            // 같은 조사판이면 **이미 걸렸다.** 폴러가 다시 눌러도 두 번 서지 않는다.
            if (java.util.Objects.equals(last.get().getResearchVersion(), now)) return Optional.empty();
            // 새 조사판이다 — 옛 주기를 물린다. 안 물리면 라운드 번호가 이어져 상한 3을
            // 옛 주기가 다 써 버린 채 새 주기가 시작도 못 한다.
            supersede(selection.getId());
        }
        String key = roundKey(selection.getId(), now, 1);
        return Optional.of(queueNextRound(selection.getSelectedByUserId(), projectId,
            selection.getId(), key));
    }

    /**
     * 지금 화면이 읽는 <b>조사판 번호</b>. 다듬기의 재료가 이것이므로 라운드에 새겨 둔다.
     *
     * <p>없으면 {@code null} — 아직 검증을 돌린 적이 없다는 뜻이고, 그때는 「같은 판인가」를
     * 물을 수 없으므로 {@link #startFirstRound} 가 옛 규칙(한 번만)대로 움직인다.
     */
    public Integer currentResearchVersion(Long projectId) {
        return latestValidationVersion(projectId).map(MarketResearchVersion::getVersionNumber).orElse(null);
    }

    /**
     * 옛 주기를 <b>현재에서 뺀다.</b> 행은 남는다.
     *
     * <p>⚠ 컨셉에 이미 적용된 변경은 되돌리지 않는다 — 물러나는 것은 제안 카드지 컨셉이 아니다.
     * 되돌리면 사용자가 승인해서 적용한 것을 시스템이 말없이 무르는 셈이 된다.
     */
    private void supersede(Long selectionId) {
        for (ConceptRefinementRound round : rounds.findBySelectionIdAndDeletedAtIsNullOrderByRoundAsc(selectionId)) {
            round.supersede();
        }
    }

    /**
     * 다음 라운드를 큐에 넣는다.
     *
     * <p>재료에 <b>직전 라운드의 기각 사유</b>를 반드시 실어 보낸다. 안 실으면 모델이 같은
     * 제안을 3라운드 내내 반복하고, 라운드 상한만 태운 채 아무것도 안 고치고 끝난다.
     */
    @Transactional
    public TaskRun queueNextRound(Long ownerId, Long projectId, Long selectionId, String idempotencyKey) {
        ConceptPortfolioSelection selection = selections.findById(selectionId).orElseThrow();
        int round = rounds.findTopBySelectionIdAndDeletedAtIsNullOrderByRoundDesc(selectionId)
            .map(value -> value.getRound() + 1).orElse(1);

        // ⚠ 뼈대는 **선택 서비스가 만든다.** 여기서 손으로 지으면 `selectedCandidate` 를
        // 빠뜨려 AI 입력 계약이 통째로 거부한다(2026-08-13 실측).
        ObjectNode input = selectionService.refinementInput("REFINE_FROM_MARKET", selection);
        input.put("expectedHypothesisRevision", selection.getHypothesisRevision());
        input.set("refinementMaterial", material(projectId, selectionId, round));
        return tasks.create(ownerId, selection, "REFINE_FROM_MARKET", input, idempotencyKey, null);
    }

    /**
     * 한 라운드가 받는 재료.
     *
     * <p>계약(동결·다듬을 수 있는 면)을 <b>입력에 실어</b> 준다. 모델에게 무엇을 건드리면
     * 안 되는지 말해 주면 버려질 제안이 줄고, 그만큼 라운드가 덜 든다.
     */
    private ObjectNode material(Long projectId, Long selectionId, int round) {
        ObjectNode material = mapper.createObjectNode();
        material.put("round", round);

        ArrayNode frozen = material.putArray("frozenFields");
        ConceptDriftContract.FROZEN_FIELDS.stream().sorted().forEach(frozen::add);
        ObjectNode refinable = material.putObject("refinableFields");
        ConceptDriftContract.REFINABLE_FIELDS.forEach(refinable::put);

        JsonNode validation = latestValidationResult(projectId);
        if (validation != null) {
            material.set("gateReasons", validation.path("bm").path("gateReasons"));
            material.set("canvas", validation.path("canvas"));
            material.set("marketEvidence",
                trimEvidence(validation.path("evidence"), validation.path("canvas")));
        }

        // 법률 소견 — 이것이 없으면 다듬기가 시장 근거만 보고, 「법이 막은 표현」은
        // 영영 안 고쳐진다. 광고 문구 칸은 동결이라 모델은 `differentiators` 로 우회해야 하는데,
        // 무엇이 막혔는지를 알려 주지 않으면 우회할 대상 자체를 모른다.
        material.set("legalFindings", legalFindings(selectionId));

        // 직전 라운드의 기각 사유 — 되먹이지 않으면 같은 제안이 반복된다.
        rounds.findTopBySelectionIdAndDeletedAtIsNullOrderByRoundDesc(selectionId).ifPresent(last -> {
            if (last.getDriftRejectionsJson() != null) {
                material.set("driftRejections", mapper.readTree(last.getDriftRejectionsJson()));
            }
            if (last.getLegalReasonsJson() != null) {
                material.set("legalRejections", mapper.readTree(last.getLegalReasonsJson()));
            }
            material.set("userDeclined", declinedOf(last));
        });
        return material;
    }

    /**
     * 그 라운드에서 <b>사람이 읽어 보고 넘긴</b> 제안들.
     *
     * <p>계약 기각·법률 차단과 <b>다른 종류</b>라 따로 보낸다 — 저 둘은 「규칙이 막았다」이고
     * 이것은 「사람이 원하지 않았다」다. 이것을 안 돌려주면 「다른 제안 받기」를 눌러도 모델이
     * 자기가 무엇을 퇴짜맞았는지 몰라 <b>같은 것을 다시 낸다.</b> 라운드는 셋뿐이라
     * 그대로 상한을 태우고 아무것도 못 고친 채 끝난다.
     *
     * <p>⚠ <b>왜 넘겼는지는 담지 않는다.</b> 화면이 묻지 않으므로 우리도 모른다 —
     * 여기서 이유를 지어 보내면 모델이 그 지어낸 이유를 근거로 다음 제안을 만든다.
     *
     * <p>아직 결정하지 않은 라운드({@code accepted_fields_json == null})는 <b>빈 배열</b>이다.
     * 「안 골랐다」를 「넘겼다」로 읽으면 사용자가 답한 적 없는 것을 답했다고 모델에 말한다.
     */
    private ArrayNode declinedOf(ConceptRefinementRound round) {
        ArrayNode declined = mapper.createArrayNode();
        if (round.getAcceptedFieldsJson() == null) return declined;
        java.util.Set<String> accepted = acceptedOf(round);
        for (JsonNode proposal : proposalsOf(round)) {
            String field = proposal.path("fieldKey").asText();
            if (accepted.contains(field)) continue;
            ObjectNode value = declined.addObject();
            value.put("fieldKey", field);
            value.put("title", proposal.path("title").asText(""));
            value.put("afterText", proposal.path("afterText").asText(""));
            value.put("rationale", proposal.path("rationale").asText(""));
        }
        return declined;
    }

    /**
     * 지금 이 컨셉에 걸려 있는 <b>법률 소견</b>을 조항과 함께 편다.
     *
     * <p>가장 최근에 본 것이 정본이다 — 델타를 돈 적이 있으면 그것, 없으면 확정된 법률보고서.
     * 둘 다 없으면 빈 배열이다(아직 법을 본 적이 없다는 뜻이고, 그때는 시장 근거로만 다듬는다).
     *
     * <p>⚠ 조문 해설이 아니라 <b>소견</b>을 보낸다. 「법이 무엇을 정하는가」를 모델에 주면
     * 모델이 그것을 그대로 컨셉에 옮겨 적는다.
     */
    private ArrayNode legalFindings(Long selectionId) {
        ArrayNode findings = mapper.createArrayNode();
        JsonNode review = latestLegalReview(selectionId);
        if (review == null) return findings;
        for (JsonNode clause : review.path("officialEvidenceReferences")) {
            for (JsonNode finding : clause.path("findings")) {
                ObjectNode value = findings.addObject();
                value.put("lawName", clause.path("lawName").asText(""));
                value.put("articleReference", clause.path("articleReference").asText(""));
                value.put("findingType", finding.path("type").asText(""));
                value.put("topic", finding.path("topic").asText(""));
                value.put("text", finding.path("text").asText(""));
            }
        }
        return findings;
    }

    /** 델타 → 없으면 확정 보고서. 저장 모양이 서로 달라 {@link #unwrapLegalReview} 로 편다. */
    private JsonNode latestLegalReview(Long selectionId) {
        var all = deltas.findAllBySelectionIdAndDeletedAtIsNullOrderByCreatedAtAsc(selectionId);
        if (!all.isEmpty()) {
            return unwrapLegalReview(mapper.readTree(all.get(all.size() - 1).getLegalReviewJson()));
        }
        return reports.findBySelectionIdAndStatusAndDeletedAtIsNull(selectionId, "CURRENT")
            .map(report -> mapper.readTree(report.getReportJson()).path("finalLegalConclusion"))
            .filter(JsonNode::isObject)
            .orElse(null);
    }

    /**
     * 저장된 델타 JSON 에서 <b>법률 검토 본문</b>을 꺼낸다.
     *
     * <p>⚠ 저장된 것은 검토 본문이 아니라 <b>액션 결과 통째</b>다
     * ({@code ConceptPortfolioSelectionMaterializationService} 의 {@code DELTA_LEGAL} 갈래가
     * {@code writeValueAsString(result)} 로 넣는다). 그래서 최상위에는
     * {@code officialEvidenceReferences} 가 없고, 그걸 바로 읽으면 <b>조용히 빈 목록</b>이 된다 —
     * 화면은 「이번에 새로 걸린 법이 없어요」로 잘못 말한다.
     *
     * <p>옛 행이 본문만 담고 있을 수도 있어 둘 다 받는다.
     */
    static JsonNode unwrapLegalReview(JsonNode stored) {
        JsonNode nested = stored.path("deltaLegalResult").path("legalReview");
        if (nested.isObject()) return nested;
        JsonNode direct = stored.path("legalReview");
        return direct.isObject() ? direct : stored;
    }

    /**
     * 다듬기 재료의 근거 상한. AI 쪽 계약(`selection_models.RefinementMaterial.marketEvidence`)이
     * <b>200장</b>이다. 그 숫자를 여기서 다시 적는 것이 아니라 <b>지키는</b> 것이다.
     */
    static final int MARKET_EVIDENCE_LIMIT = 200;

    /**
     * 봉투의 근거를 다듬기가 받을 수 있는 크기로 <b>추린다</b>.
     *
     * <p>⚠ 2026-08-15 실측: 시장조사가 절 사실을 승격시키기 시작하면서 봉투 근거가
     * <b>422장 · 274KB</b>가 됐다(유료 실행 {@code p46-bm-01}). 그대로 넘기면 AI 계약이
     * 200장 상한에서 거부해 <b>컨셉 다듬기가 400 으로 죽는다.</b> 화면은 422장을 그대로
     * 보여야 하므로 봉투를 줄이는 것이 아니라 <b>이 재료만</b> 줄인다.
     *
     * <p>무엇을 남기나 — <b>캔버스 9칸이 실제로 인용한 근거</b>다. 그것이 BM 판정을 세운
     * 근거이고, 다듬기가 고쳐야 할 대상도 그 판정이다. 남는 자리는 실린 순서대로 채운다
     * (임의 표본을 쓰면 어느 절만 통째로 빠진다 — 실린 순서가 이미 절별로 묶여 있다).
     *
     * <p>⚠ 나머지를 버리는 것이 <b>안전할 뿐 아니라 옳다.</b> 2026-08-15 실측에서 절 조사가
     * 승격시킨 사실 396장의 상당수가 이 사업과 무관했다(「가업승계 비율」·「국민의 취침
     * 시각」·「전세보증금 평균」). 그것을 다듬기 모델에게 「시장 근거」라고 주면
     * <b>근거 있는 척하는 제안</b>이 늘어난다 — {@code drift.filter_ungrounded} 가 막으려던
     * 바로 그것이다.
     *
     * <p>⚠ 잘려 나간 근거를 모델이 인용하는 일은 <b>구조적으로 생기지 않는다.</b> 프롬프트가
     * 이 목록만 보여 주고, {@code drift.filter_ungrounded} 가 목록 밖 id 를 기각한다.
     */
    static JsonNode trimEvidence(JsonNode evidence, JsonNode canvas) {
        if (!evidence.isArray() || evidence.size() <= MARKET_EVIDENCE_LIMIT) return evidence;
        java.util.Set<String> cited = new java.util.HashSet<>();
        for (JsonNode cell : canvas.path("cells")) {
            for (JsonNode ref : cell.path("marketEvidenceIds")) cited.add(ref.asString(""));
        }
        ArrayNode kept = JsonNodeFactory.instance.arrayNode();
        for (boolean rest : new boolean[] {false, true}) {
            for (JsonNode item : evidence) {
                if (kept.size() >= MARKET_EVIDENCE_LIMIT) return kept;
                if (cited.contains(item.path("id").asString("")) != rest) kept.add(item);
            }
        }
        return kept;
    }

    /** 검증 결과 — 새 실행(VALIDATION)이 있으면 그것, 없으면 옛 BM 것을 읽는다. */
    private JsonNode latestValidationResult(Long projectId) {
        return latestValidationVersion(projectId).map(v -> mapper.readTree(v.getResultJson())).orElse(null);
    }

    /**
     * 다듬기가 재료로 삼는 <b>조사판 한 벌</b>. 내용과 번호가 <b>같은 곳에서</b> 나와야 한다 —
     * 두 벌로 베끼면 「어느 판을 근거로 만들었나」와 실제 재료가 조용히 갈린다.
     */
    private Optional<MarketResearchVersion> latestValidationVersion(Long projectId) {
        for (MarketResearchRun.Kind kind : List.of(MarketResearchRun.Kind.VALIDATION, MarketResearchRun.Kind.BM)) {
            Optional<MarketResearchVersion> version = researchVersions
                .findTopByProjectIdAndKindAndDeletedAtIsNullOrderByVersionNumberDesc(projectId, kind);
            if (version.isPresent()) return version;
        }
        return Optional.empty();
    }

    /** 라운드가 통과시킨 제안. 없으면 「고칠 것 없음」이다. */
    @Transactional(readOnly = true)
    public JsonNode proposalsOf(ConceptRefinementRound round) {
        return round.getProposalJson() == null ? mapper.createArrayNode()
            : mapper.readTree(round.getProposalJson());
    }

    /** 계약이 기각한 제안과 사유. 최종 화면의 「못 푼 것」이 여기서 나온다. */
    @Transactional(readOnly = true)
    public JsonNode rejectionsOf(ConceptRefinementRound round) {
        return round.getDriftRejectionsJson() == null ? mapper.createArrayNode()
            : mapper.readTree(round.getDriftRejectionsJson());
    }

    /**
     * <b>법률이 막은 사유.</b> 화면의 「못 푼 것」이 이것도 보여야 한다.
     *
     * <p>⚠ <b>2026-08-15 정정.</b> 이 주석은 「이 칸이 화면 어디로도 안 갔다」고 적고 있었다.
     * <b>틀렸다</b> — {@code ConceptRefinementController.unresolvedOf} 가 이미 읽어 「못 푼 것」에
     * 싣는다. 낡은 말이라 지운다.
     *
     * <p>다만 그 <b>결말 이름</b>은 실제로 거짓이었다. 법이 막은 라운드를 화면이
     * 「고칠 것 없음 — 컨셉은 그대로예요」라고 불렀다. 그것은 같은 판에서
     * {@code outcomeOf} 의 {@code LEGAL_BLOCKED} 로 갈랐다. 사유는 여기서 계속 나간다.
     */
    @Transactional(readOnly = true)
    public JsonNode legalReasonsOf(ConceptRefinementRound round) {
        return round.getLegalReasonsJson() == null ? mapper.createArrayNode()
            : mapper.readTree(round.getLegalReasonsJson());
    }

    /**
     * 서술문에서 <b>초록으로 물들 말</b>. 「이 변경이 정말 문단에 있나」를 재는 잣대이기도 하다.
     *
     * <p>목록 칸은 값 전체가 아니라 <b>더해진 항목만</b> 표시한다. 차별점 네 개에 하나를 더한
     * 변경에서 값 전체를 요구하면, 문단은 목록을 통째로 옮겨 적어야 하고 <b>정작 더해진 항목은
     * 빠져도 통과</b>한다 — 2026-08-13 실측에서 정확히 그 일이 났다(모델이 옛 네 항목만 적고
     * 새 항목을 빠뜨렸다). 더해진 항목을 재면 「무엇이 바뀌었나」를 곧장 재는 것이 된다.
     *
     * <p>더해진 것이 없으면(가격처럼 값 하나가 통째로 바뀌면) 바뀐 값 전체가 잣대다.
     */
    public static String changeMark(String beforeText, String afterText) {
        List<String> before = items(beforeText);
        List<String> after = items(afterText);
        for (String item : after) {
            if (!before.contains(item)) return item;
        }
        return afterText == null ? "" : afterText.trim();
    }

    /**
     * 「가, 나, 다」를 항목으로 편다.
     *
     * <p>안 자르는 자리가 둘이다: <b>괄호 안</b>(한 항목 안의 나열이다)과
     * <b>숫자 사이의 쉼표</b>(「8,900원」의 천 단위다 — 자르면 가격이 「1팩 8」과 「900원」이 된다).
     */
    private static List<String> items(String text) {
        List<String> values = new java.util.ArrayList<>();
        if (text == null || text.isBlank()) return values;
        StringBuilder buffer = new StringBuilder();
        int depth = 0;
        char[] chars = text.toCharArray();
        for (int at = 0; at < chars.length; at++) {
            char c = chars[at];
            if (c == '(' || c == '[') depth++;
            else if (c == ')' || c == ']') depth = Math.max(0, depth - 1);
            boolean thousands = c == ',' && at > 0 && at + 1 < chars.length
                && Character.isDigit(chars[at - 1]) && Character.isDigit(chars[at + 1]);
            if (depth == 0 && !thousands && (c == ',' || c == '·')) {
                if (!buffer.toString().isBlank()) values.add(buffer.toString().trim());
                buffer.setLength(0);
            } else buffer.append(c);
        }
        if (!buffer.toString().isBlank()) values.add(buffer.toString().trim());
        return values;
    }

    /** 이력 — 최종 화면의 변경 표와 「못 푼 것」 사유가 여기서 나온다. */
    @Transactional(readOnly = true)
    public List<ConceptRefinementRound> history(Long selectionId) {
        return rounds.findBySelectionIdAndDeletedAtIsNullOrderByRoundAsc(selectionId);
    }

    /**
     * 이 선택이 <b>컨셉 다듬기를 지났는가.</b> 시드에 찍히고, 시장 인터뷰 게이트가 그 값을 본다.
     *
     * <p>판정은 <b>라운드 행이 하나라도 있는가</b>다. 라운드 행은 채택 성공 때만 생기므로
     * (「다듬기가 실제로 한 번은 돌았다」와 같은 말이다) 실패로 끝난 다듬기는 여기서 걸러진다.
     *
     * <p>⚠ 「고칠 것이 나오지 않았다」({@code NOTHING_TO_FIX})도 <b>지난 것</b>이다. 1라운드가
     * 돌아 「바꿀 것 없음」이라는 답을 받은 것이지, 안 돌아본 것이 아니다. 이것을 안 지난 것으로
     * 세면 컨셉이 이미 좋은 프로젝트가 영영 인터뷰를 못 한다.
     */
    @Transactional(readOnly = true)
    public boolean refined(Long selectionId) {
        return !rounds.findBySelectionIdAndDeletedAtIsNullOrderByRoundAsc(selectionId).isEmpty();
    }

    /**
     * 최종 컨셉 서술문. <b>없으면 빈 목록</b>이고, 그때 화면은 칸 나열로 폴백한다.
     *
     * <p>없는 경우는 셋이다: 아직 수렴 전 · 서술문이 검증을 통과 못 함 · 바뀐 것이 없음.
     * 셋 다 「비어 있다」로 같게 다뤄도 된다 — 어느 쪽이든 <b>세울 문장이 없다</b>는 뜻이다.
     */
    @Transactional(readOnly = true)
    public List<ConceptRefinementController.NarrativeSegment> narrativeOf(Long selectionId) {
        return finals.findBySelectionIdAndDeletedAtIsNull(selectionId)
            .map(ConceptRefinementFinal::getNarrativeJson)
            .map(json -> {
                List<ConceptRefinementController.NarrativeSegment> segments = new java.util.ArrayList<>();
                for (JsonNode node : mapper.readTree(json)) {
                    JsonNode ref = node.path("changeRef");
                    segments.add(new ConceptRefinementController.NarrativeSegment(
                        node.path("text").asText(""), ref.isInt() ? ref.asInt() : null));
                }
                return segments;
            })
            .orElse(List.of());
    }

    /**
     * 검증을 통과한 서술문을 적는다.
     *
     * <p>⚠ <b>검증은 호출자가 이미 했다.</b> 여기서 다시 보지 않는다 — 두 곳에서 보면
     * 규칙이 갈리고, 어느 쪽이 정본인지 모르게 된다.
     */
    @Transactional
    public void recordNarrative(Long projectId, Long selectionId, String narrativeJson) {
        ConceptRefinementFinal row = finals.findBySelectionIdAndDeletedAtIsNull(selectionId)
            .orElseGet(() -> finals.save(ConceptRefinementFinal.of(projectId, selectionId)));
        row.recordNarrative(narrativeJson);
        finals.save(row);
    }

    /** 최종 확정이 시드에 얹을 오버레이. 없으면 {@code null} — 얹을 것이 없다는 뜻이다. */
    @Transactional(readOnly = true)
    public JsonNode overlayOf(Long selectionId) {
        return finals.findBySelectionIdAndDeletedAtIsNull(selectionId)
            .map(ConceptRefinementFinal::getOverlayJson)
            .filter(json -> json != null && !json.isBlank())
            .map(mapper::readTree)
            .orElse(null);
    }

    /**
     * 수렴한 뒤 <b>한 번만</b> 서술문을 건다.
     *
     * <p>⚠ 라운드마다 걸면 중간값이 남은 문장이 최종 문장 자리에 선다. 그리고 LLM 을
     * 세 번 태운다.
     *
     * <p>바뀐 것이 하나도 없으면 걸지 않는다 — 고칠 것이 없었던 컨셉에 새 문장을 씌우면
     * 「무엇이 바뀌었나」가 없는 채로 문장만 달라진다.
     *
     * @return 건 TaskRun. 걸 이유가 없었으면 비어 있다.
     */
    @Transactional
    public Optional<TaskRun> queueNarration(Long ownerId, Long projectId, Long selectionId) {
        ConceptPortfolioSelection selection = selections.findById(selectionId).orElseThrow();
        ArrayNode changes = mapper.createArrayNode();
        for (ConceptRefinementRound round : history(selectionId)) {
            // ⚠ **사람이 고른 것만 서술문 입력에 넣는다.** 전량을 넣으면 모델이 체크되지 않은
            //   변경까지 최종 컨셉 문장에 담고, 그 문장이 곧 최종 컨셉이 된다.
            //   잣대(`narrativeMatchesChanges`)와 **같은 규칙**이어야 한다 — 갈리면 모델은
            //   A 를 담으라는 말을 듣고 서버는 B 를 찾는다.
            java.util.Set<String> accepted = acceptedOf(round);
            for (JsonNode proposal : proposalsOf(round)) {
                if (round.getAcceptedFieldsJson() != null
                    && !accepted.contains(proposal.path("fieldKey").asText())) continue;
                ObjectNode change = ((ObjectNode) proposal).deepCopy();
                // 문단이 실제로 담아야 할 말. 검증도 같은 잣대를 쓴다.
                change.put("mark", changeMark(proposal.path("beforeText").asText(""),
                    proposal.path("afterText").asText("")));
                changes.add(change);
            }
        }
        if (changes.isEmpty()) return Optional.empty();
        if (finals.findBySelectionIdAndDeletedAtIsNull(selectionId)
                .map(value -> value.getNarrativeJson() != null).orElse(false)) {
            return Optional.empty();
        }

        ObjectNode input = selectionService.refinementInput("NARRATE_REFINED", selection);
        input.put("expectedHypothesisRevision", selection.getHypothesisRevision());
        input.putObject("narrationMaterial").set("changes", changes);
        return Optional.of(tasks.create(ownerId, selection, "NARRATE_REFINED", input,
            "narrate:" + selectionId, null));
    }
}
