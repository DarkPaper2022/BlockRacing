#!/usr/bin/env bash
# Handoff P0 acceptance: one real Paper server + three Fabric clients (two red, one blue).
# Covers the Tab board via real X11 input, shared team progress, interactive action
# targets, a mid-round Paper restart, bonus scoring and single settlement.
# See docs/e2e-testing-and-mock-spec.md ("P0 验收").
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUNTIME_DIR="${BLOCKRACING_E2E_RUNTIME:-${ROOT_DIR}/target/e2e-runtime-acceptance}"
PAPER_JAR="${BLOCKRACING_PAPER_JAR:-/home/darkpaper/Game/blockracing-draftout/server.jar}"
SERVER_JAVA="${BLOCKRACING_SERVER_JAVA:-/usr/lib/jvm/java-27-openjdk/bin/java}"
SERVER_PORT="${BLOCKRACING_E2E_PORT:-25576}"
LEVEL_SEED="${BLOCKRACING_E2E_LEVEL_SEED:-blockracing-acceptance-20261001}"
RUN_LOG_DIR="${ROOT_DIR}/target/e2e-logs/acceptance-$(date +%Y%m%d-%H%M%S)"
SYNC_DIR="${RUN_LOG_DIR}/sync"
SERVER_INPUT="${RUN_LOG_DIR}/server.stdin"
# Order matters only for the board; the client asserts scores from the board itself.
# Goal targets are keyed as DRAFTOUT:<csv id>; plain block targets use the Material name.
TARGETS="DRAFTOUT:GET_30_ADVANCEMENTS,DRAFTOUT:OBTAIN_5_UNIQUE_DISCS,DRAFTOUT:KILL_7_UNIQUE_HOSTILE_MOBS,\
DRAFTOUT:TAKE_200_DAMAGE,DRAFTOUT:USE_LOOM,DRAFTOUT:REMOVE_STATUS_EFFECT_USING_MILK,DRAFTOUT:USE_CAULDRON,\
DRAFTOUT:USE_COMPOSTER,DRAFTOUT:USE_JUKEBOX,DRAFTOUT:MINE_DIAMOND_ORE,DRAFTOUT:MINE_EMERALD_ORE,NETHERITE_BLOCK,TRIDENT,PHANTOM_MEMBRANE,\
WITHER_SKELETON_SKULL,SNIFFER_EGG,TOTEM_OF_UNDYING,NETHERITE_INGOT,MYCELIUM,\
SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE,ZOMBIE_HEAD"
CLIENTS=("TestBot_RedA red red_a 121 true" "TestBot_RedB red red_b 122 false" "TestBot_Blue blue blue 123 false")
SERVER_PID=""
CLIENT_PIDS=()

mkdir -p "${RUNTIME_DIR}/plugins" "${RUN_LOG_DIR}" "${SYNC_DIR}"

cleanup() {
  set +e
  for pid in "${CLIENT_PIDS[@]}"; do
    if kill -0 "${pid}" 2>/dev/null; then kill -TERM -- "-${pid}" 2>/dev/null || kill -TERM "${pid}"; fi
  done
  stop_server
}
trap cleanup EXIT INT TERM

stop_server() {
  if [[ -n "${SERVER_PID}" ]] && kill -0 "${SERVER_PID}" 2>/dev/null; then
    printf 'stop\n' >&9 2>/dev/null
    for _ in {1..150}; do
      kill -0 "${SERVER_PID}" 2>/dev/null || break
      sleep 0.2
    done
    if kill -0 "${SERVER_PID}" 2>/dev/null; then
      kill -TERM -- "-${SERVER_PID}" 2>/dev/null || kill -TERM "${SERVER_PID}" 2>/dev/null
    fi
    wait "${SERVER_PID}" 2>/dev/null
  fi
  SERVER_PID=""
}

start_server() {
  local log="$1"
  cd "${RUNTIME_DIR}"
  setsid "${SERVER_JAVA}" -Xms1G -Xmx2G "-Dblockracing.test.targets=${TARGETS}" \
    -Dblockracing.rtp.max-inflight=6 -DPaper.WorkerThreadCount=7 \
    -jar "${PAPER_JAR}" --nogui <"${SERVER_INPUT}" > >(tee "${log}") 2>&1 &
  SERVER_PID=$!
  cd "${ROOT_DIR}"
  local deadline=$((SECONDS + 180))
  until rg -q 'Done \(' "${log}" 2>/dev/null; do
    if ! kill -0 "${SERVER_PID}" 2>/dev/null; then echo "Paper exited; see ${log}" >&2; exit 1; fi
    if (( SECONDS >= deadline )); then echo "Timed out starting Paper; see ${log}" >&2; exit 1; fi
    sleep 1
  done
}

[[ -r "${PAPER_JAR}" ]] || { echo "Paper jar not found: ${PAPER_JAR}" >&2; exit 2; }

echo "[1/6] Building server and client"
(cd "${ROOT_DIR}" && JAVA_HOME=/usr/lib/jvm/java-27-openjdk mvn package -q)
(cd "${ROOT_DIR}/client-mod" && \
  JAVA_HOME=/home/darkpaper/.local/share/hmcl/java/linux-x86_64/mojang-java-runtime-epsilon ./gradlew classes downloadAssets -q)

cp "${ROOT_DIR}/target/BlockRacing-26.2.1.jar" "${RUNTIME_DIR}/plugins/BlockRacing.jar"
rm -f "${RUNTIME_DIR}/plugins/BlockRacing/game-progress.yml" "${RUNTIME_DIR}/plugins/BlockRacing/game-progress.yml.tmp"
echo 'eula=true' >"${RUNTIME_DIR}/eula.txt"
cat >"${RUNTIME_DIR}/server.properties" <<EOF
server-port=${SERVER_PORT}
online-mode=false
enforce-secure-profile=false
motd=BlockRacing acceptance
gamemode=survival
difficulty=peaceful
spawn-protection=0
view-distance=10
simulation-distance=8
max-players=6
sync-chunk-writes=false
level-seed=${LEVEL_SEED}
EOF

echo "[2/6] Starting Paper on ${SERVER_PORT} (runtime ${RUNTIME_DIR})"
mkfifo "${SERVER_INPUT}"
exec 9<>"${SERVER_INPUT}"
start_server "${RUN_LOG_DIR}/server-1.log"
# Test-runtime authority only: OP lets the clients issue vanilla commands; natural
# spawning is off so stray mobs cannot add damage/kills to the shared ledgers.
for client in "${CLIENTS[@]}"; do read -r name _ <<<"${client}"; printf 'op %s\n' "${name}" >&9; done
printf 'gamerule spawn_mobs false\ngamerule immediate_respawn true\n' >&9

echo "[3/6] Starting three Fabric clients"
for client in "${CLIENTS[@]}"; do
  read -r name team role display coordinator <<<"${client}"
  BLOCKRACING_TEST_SCENARIO=acceptance BLOCKRACING_TEST_ROLE="${role}" \
  BLOCKRACING_TEST_SYNC_DIR="${SYNC_DIR}" BLOCKRACING_TEST_DISPLAY="${display}" \
    setsid "${ROOT_DIR}/client-mod/run-test-client.sh" "${name}" "${team}" 30 3 8388608 1 2 true \
    "${SERVER_PORT}" "${coordinator}" "${TARGETS}" >"${RUN_LOG_DIR}/${role}.log" 2>&1 &
  CLIENT_PIDS+=("$!")
  sleep 5
done

clients_alive() {
  local pid
  for pid in "${CLIENT_PIDS[@]}"; do kill -0 "${pid}" 2>/dev/null && return 0; done
  return 1
}

echo "[4/6] Running scenario until all clients request the restart"
deadline=$((SECONDS + 1800))
until (( $(cat "${RUN_LOG_DIR}"/{red_a,red_b,blue}.log 2>/dev/null | rg -c 'BLOCKRACING_E2E AWAIT_RESTART' || true) >= 3 )); do
  if rg -q 'Refusing to start' "${RUN_LOG_DIR}/server-1.log"; then
    rg 'Refusing to start' "${RUN_LOG_DIR}/server-1.log" >&2
    exit 1
  fi
  if rg -q 'BLOCKRACING_E2E FAIL' "${RUN_LOG_DIR}"/{red_a,red_b,blue}.log 2>/dev/null || ! clients_alive; then
    rg 'BLOCKRACING_E2E FAIL' "${RUN_LOG_DIR}"/*.log >&2 || true
    echo "Scenario failed before restart; logs: ${RUN_LOG_DIR}" >&2
    exit 1
  fi
  (( SECONDS < deadline )) || { echo "Timed out waiting for restart point" >&2; exit 1; }
  sleep 2
done

echo "[5/6] Normal Paper stop + restart with the saved round"
sleep 6   # let the 5 s autosave run once more; onDisable also saves
stop_server
cp "${RUNTIME_DIR}/plugins/BlockRacing/game-progress.yml" "${RUN_LOG_DIR}/game-progress-at-restart.yml"
start_server "${RUN_LOG_DIR}/server-2.log"

echo "[6/6] Waiting for clients to finish"
for pid in "${CLIENT_PIDS[@]}"; do wait "${pid}" || true; done
CLIENT_PIDS=()

status=0
for role in red_a red_b blue; do
  if ! rg -q "BLOCKRACING_E2E PASS role=${role}" "${RUN_LOG_DIR}/${role}.log"; then
    echo "Missing PASS for ${role}" >&2
    status=1
  fi
done
rg 'BLOCKRACING_E2E (FAIL|FINAL|BOARD|SNAPSHOT|RECOVERED)' "${RUN_LOG_DIR}"/{red_a,red_b,blue}.log || true
settled="$(cat "${RUN_LOG_DIR}"/server-*.log | rg -c 'Round settled:' || true)"
echo "Round settled lines: ${settled:-0}"
if [[ "${settled:-0}" != "1" ]]; then
  echo "Expected exactly one settlement" >&2
  status=1
fi
rg -q 'Round settled: winner=red' "${RUN_LOG_DIR}"/server-*.log || { echo "Red should win" >&2; status=1; }
if (( status != 0 )); then echo "Acceptance FAILED; logs: ${RUN_LOG_DIR}" >&2; exit 1; fi
echo "Acceptance passed. Logs and screenshots: ${RUN_LOG_DIR}"
