#!/usr/bin/env bash
# One real Paper server + two isolated Fabric clients, with a reusable world.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUNTIME_DIR="${BLOCKRACING_E2E_RUNTIME:-${ROOT_DIR}/target/e2e-runtime}"
PAPER_JAR="${BLOCKRACING_PAPER_JAR:-/home/darkpaper/Game/blockracing-draftout/server.jar}"
SERVER_JAVA="${BLOCKRACING_SERVER_JAVA:-/usr/lib/jvm/java-27-openjdk/bin/java}"
SERVER_PORT="${BLOCKRACING_E2E_PORT:-25575}"
TARGETS="${BLOCKRACING_E2E_TARGETS:-CHERRY_PLANKS,STONE,DIRT,COBBLESTONE}"
SERVER_XMS="${BLOCKRACING_E2E_XMS:-1G}"
SERVER_XMX="${BLOCKRACING_E2E_XMX:-2G}"
WORKER_THREADS="${BLOCKRACING_E2E_WORKER_THREADS:-7}"
MAX_INFLIGHT="${BLOCKRACING_E2E_MAX_INFLIGHT:-6}"
RTP_SEED="${BLOCKRACING_E2E_RTP_SEED:-20260929}"
LEVEL_SEED="${BLOCKRACING_E2E_LEVEL_SEED:-blockracing-e2e-20260929}"
RUN_LOG_DIR="${ROOT_DIR}/target/e2e-logs/$(date +%Y%m%d-%H%M%S)"
RED_NAME="TestBot_Red"
BLUE_NAME="TestBot_Blue"
SERVER_PID=""
RED_PID=""
BLUE_PID=""
SERVER_SAMPLER_PID=""
RED_SAMPLER_PID=""
BLUE_SAMPLER_PID=""
SERVER_INPUT="${RUN_LOG_DIR}/server.stdin"

mkdir -p "${RUNTIME_DIR}/plugins" "${RUN_LOG_DIR}"

if [[ ! "${SERVER_XMS}" =~ ^[1-9][0-9]*[mMgG]$ || ! "${SERVER_XMX}" =~ ^[1-9][0-9]*[mMgG]$ ]]; then
  echo "BLOCKRACING_E2E_XMS/XMX must look like 1G or 2048M" >&2
  exit 2
fi
if [[ ! "${MAX_INFLIGHT}" =~ ^[1-9][0-9]*$ ]]; then
  echo "BLOCKRACING_E2E_MAX_INFLIGHT must be a positive integer" >&2
  exit 2
fi
if [[ "${WORKER_THREADS}" != "auto" && ! "${WORKER_THREADS}" =~ ^[1-9][0-9]*$ ]]; then
  echo "BLOCKRACING_E2E_WORKER_THREADS must be 'auto' or a positive integer" >&2
  exit 2
fi

SERVER_JVM_ARGS=(
  "-Xms${SERVER_XMS}"
  "-Xmx${SERVER_XMX}"
  "-Dblockracing.test.targets=${TARGETS}"
  "-Dblockracing.rtp.max-inflight=${MAX_INFLIGHT}"
  "-Dblockracing.test.rtp.seed=${RTP_SEED}"
)
if [[ "${WORKER_THREADS}" != "auto" ]]; then
  SERVER_JVM_ARGS+=("-DPaper.WorkerThreadCount=${WORKER_THREADS}")
fi

sample_process_group_rss() {
  local process_group="$1"
  local output_file="$2"
  local peak_kib=0
  local current_kib
  while kill -0 "${process_group}" 2>/dev/null; do
    current_kib="$(ps -eo pgid=,rss= | awk -v wanted="${process_group}" \
      '$1 == wanted { total += $2 } END { print total + 0 }')"
    if (( current_kib > peak_kib )); then peak_kib="${current_kib}"; fi
    sleep 1
  done
  printf 'Peak process-group RSS (kbytes): %s\n' "${peak_kib}" >"${output_file}"
}

cleanup() {
  set +e
  for pid in "${RED_PID}" "${BLUE_PID}"; do
    if [[ -n "${pid}" ]] && kill -0 "${pid}" 2>/dev/null; then
      # Each launch below owns a fresh session, so this targets only that
      # client's time/Xvfb/Gradle/Java process tree.
      kill -TERM -- "-${pid}" 2>/dev/null || kill -TERM "${pid}" 2>/dev/null
    fi
  done
  if [[ -n "${SERVER_PID}" ]] && kill -0 "${SERVER_PID}" 2>/dev/null; then
    printf 'stop\n' >&9 2>/dev/null
    for _ in {1..30}; do
      kill -0 "${SERVER_PID}" 2>/dev/null || break
      sleep 0.2
    done
    if kill -0 "${SERVER_PID}" 2>/dev/null; then
      kill -TERM -- "-${SERVER_PID}" 2>/dev/null || kill -TERM "${SERVER_PID}" 2>/dev/null
    fi
    wait "${SERVER_PID}" 2>/dev/null
  fi
  for pid in "${RED_SAMPLER_PID}" "${BLUE_SAMPLER_PID}" "${SERVER_SAMPLER_PID}"; do
    if [[ -n "${pid}" ]]; then wait "${pid}" 2>/dev/null; fi
  done
}
trap cleanup EXIT INT TERM

if [[ ! -r "${PAPER_JAR}" ]]; then
  echo "Paper jar not found: ${PAPER_JAR}" >&2
  exit 2
fi

echo "[1/5] Building server and client tests"
(cd "${ROOT_DIR}" && JAVA_HOME=/usr/lib/jvm/java-27-openjdk mvn package -q)
(cd "${ROOT_DIR}/client-mod" && \
  JAVA_HOME=/home/darkpaper/.local/share/hmcl/java/linux-x86_64/mojang-java-runtime-epsilon ./gradlew test classes)

cp "${ROOT_DIR}/target/BlockRacing-26.2.1.jar" "${RUNTIME_DIR}/plugins/BlockRacing.jar"
rm -f "${RUNTIME_DIR}/plugins/BlockRacing/game-progress.yml" \
      "${RUNTIME_DIR}/plugins/BlockRacing/game-progress.yml.tmp"

cat >"${RUNTIME_DIR}/eula.txt" <<'EOF'
eula=true
EOF
cat >"${RUNTIME_DIR}/server.properties" <<EOF
server-port=${SERVER_PORT}
online-mode=false
enforce-secure-profile=false
motd=BlockRacing isolated E2E
gamemode=survival
difficulty=peaceful
spawn-protection=0
view-distance=12
simulation-distance=8
max-players=4
sync-chunk-writes=false
level-seed=${LEVEL_SEED}
EOF

echo "[2/5] Starting isolated Paper server on ${SERVER_PORT} (runtime: ${RUNTIME_DIR})"
echo "      Xms=${SERVER_XMS} Xmx=${SERVER_XMX} workers=${WORKER_THREADS} inflight=${MAX_INFLIGHT} level-seed=${LEVEL_SEED} rtp-seed=${RTP_SEED}"
cd "${RUNTIME_DIR}"
mkfifo "${SERVER_INPUT}"
exec 9<>"${SERVER_INPUT}"
setsid /usr/bin/time -v -o "${RUN_LOG_DIR}/server.resources" \
  "${SERVER_JAVA}" "${SERVER_JVM_ARGS[@]}" \
  -jar "${PAPER_JAR}" --nogui <"${SERVER_INPUT}" \
  > >(tee "${RUN_LOG_DIR}/server.log") 2>&1 &
SERVER_PID=$!
sample_process_group_rss "${SERVER_PID}" "${RUN_LOG_DIR}/server.group.resources" &
SERVER_SAMPLER_PID=$!

deadline=$((SECONDS + 180))
until rg -q 'Done \(' "${RUN_LOG_DIR}/server.log" 2>/dev/null; do
  if ! kill -0 "${SERVER_PID}" 2>/dev/null; then
    echo "Paper exited before startup; see ${RUN_LOG_DIR}/server.log" >&2
    exit 1
  fi
  if (( SECONDS >= deadline )); then
    echo "Timed out starting Paper; see ${RUN_LOG_DIR}/server.log" >&2
    exit 1
  fi
  sleep 1
done

actual_workers="$(rg -o 'Paper is using [0-9]+ worker threads' "${RUN_LOG_DIR}/server.log" \
  | tail -1 | rg -o '[0-9]+' || true)"
if [[ -z "${actual_workers}" ]]; then
  echo "Could not determine Paper worker count from server log" >&2
  exit 1
fi
if [[ "${WORKER_THREADS}" != "auto" && "${actual_workers}" != "${WORKER_THREADS}" ]]; then
  echo "Paper worker mismatch: requested=${WORKER_THREADS}, actual=${actual_workers}" >&2
  exit 1
fi
echo "      Paper reported ${actual_workers} worker threads"

# The clients issue only vanilla/plugin commands. OP is test-runtime authority,
# not a server-side completion hook; all completion checks remain production code.
printf 'op %s\nop %s\n' "${RED_NAME}" "${BLUE_NAME}" >&9

echo "[3/5] Starting two Fabric clients"
cd "${ROOT_DIR}"
setsid /usr/bin/time -v -o "${RUN_LOG_DIR}/red.resources" \
  "${ROOT_DIR}/client-mod/run-test-client.sh" "${RED_NAME}" red 60 5 3145728 2 2 true \
  "${SERVER_PORT}" true "${TARGETS}" >"${RUN_LOG_DIR}/red.log" 2>&1 &
RED_PID=$!
sample_process_group_rss "${RED_PID}" "${RUN_LOG_DIR}/red.group.resources" &
RED_SAMPLER_PID=$!
setsid /usr/bin/time -v -o "${RUN_LOG_DIR}/blue.resources" \
  "${ROOT_DIR}/client-mod/run-test-client.sh" "${BLUE_NAME}" blue 60 5 3145728 1 2 true \
  "${SERVER_PORT}" false "${TARGETS}" >"${RUN_LOG_DIR}/blue.log" 2>&1 &
BLUE_PID=$!
sample_process_group_rss "${BLUE_PID}" "${RUN_LOG_DIR}/blue.group.resources" &
BLUE_SAMPLER_PID=$!

echo "[4/5] Waiting for clients (game start must not wait for RTP pre-generation)"
red_status=0
blue_status=0
wait "${RED_PID}" || red_status=$?
RED_PID=""
wait "${BLUE_PID}" || blue_status=$?
BLUE_PID=""
wait "${RED_SAMPLER_PID}"
RED_SAMPLER_PID=""
wait "${BLUE_SAMPLER_PID}"
BLUE_SAMPLER_PID=""

echo "[5/5] Verifying cross-client assertions"
if (( red_status != 0 || blue_status != 0 )); then
  echo "Client process failed: red=${red_status}, blue=${blue_status}" >&2
  exit 1
fi
if rg -q 'BLOCKRACING_E2E FAIL' "${RUN_LOG_DIR}/red.log" "${RUN_LOG_DIR}/blue.log"; then
  rg 'BLOCKRACING_E2E (FAIL|PASS)' "${RUN_LOG_DIR}/red.log" "${RUN_LOG_DIR}/blue.log" >&2
  exit 1
fi
for log in red blue; do
  if ! rg -q 'BLOCKRACING_E2E PASS' "${RUN_LOG_DIR}/${log}.log"; then
    echo "Missing PASS marker in ${log}.log" >&2
    exit 1
  fi
done

red_score="$(rg -o 'MUTEX_RESULT score=[0-9]+' "${RUN_LOG_DIR}/red.log" | tail -1 | cut -d= -f2)"
blue_score="$(rg -o 'MUTEX_RESULT score=[0-9]+' "${RUN_LOG_DIR}/blue.log" | tail -1 | cut -d= -f2)"
target_score="$(rg -o 'targetScore=[0-9]+' "${RUN_LOG_DIR}/red.log" | tail -1 | cut -d= -f2)"
if [[ -z "${red_score}" || -z "${blue_score}" || -z "${target_score}" ]] \
    || (( red_score + blue_score != target_score )); then
  echo "Mutual exclusion assertion failed: red=${red_score:-?}, blue=${blue_score:-?}, target=${target_score:-?}" >&2
  exit 1
fi

rg 'BLOCKRACING_E2E (BOARD_VALIDATED|FAVORITE_ISOLATION_OK|MUTEX_RESULT|RTP_RESULT|PASS)' \
  "${RUN_LOG_DIR}/red.log" "${RUN_LOG_DIR}/blue.log"
echo "E2E passed. Logs: ${RUN_LOG_DIR}"
