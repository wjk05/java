#!/usr/bin/env bash
# =============================================================================
# Ruici AI Platform - Kubernetes 一键部署脚本
# -----------------------------------------------------------------------------
# 功能：
#   1. 构建后端（Spring Boot）与前端（React + Nginx）镜像
#   2. 镜像推送到仓库，或直接导入本机 containerd（无仓库也能部署）
#   3. 创建命名空间、Secret / ConfigMap
#   4. 部署 PostgreSQL(pgvector) + Redis + MinIO + 后端 + 前端（+ 可选 Ingress）
#   5. 等待所有工作负载就绪并打印访问地址
#
# 用法：
#   ./deploy.sh                # 构建 + 部署（最常用）
#   ./deploy.sh --skip-build   # 跳过构建，直接用现有镜像重新部署
#   ./deploy.sh --build-only   # 只构建并发布镜像
#   ./deploy.sh --delete       # 删除整个命名空间（会删除 PVC 数据！）
#
# 依赖：bash、kubectl、docker（或 nerdctl/podman）
# 配置：同目录 deploy.env（首次运行会自动从 deploy.env.example 生成）
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
MANIFEST_DIR="${SCRIPT_DIR}/manifests"
RENDER_DIR="${SCRIPT_DIR}/.rendered"
ENV_FILE="${SCRIPT_DIR}/deploy.env"
ENV_EXAMPLE="${SCRIPT_DIR}/deploy.env.example"

log()  { printf '\033[1;34m[ruici]\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[ruici][warn]\033[0m %s\n' "$*"; }
die()  { printf '\033[1;31m[ruici][error]\033[0m %s\n' "$*" >&2; exit 1; }

usage() {
  sed -n '2,20p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
  exit 0
}

MODE="deploy"
SKIP_BUILD=0
for arg in "$@"; do
  case "$arg" in
    --skip-build) SKIP_BUILD=1 ;;
    --build-only) MODE="build-only" ;;
    --delete)     MODE="delete" ;;
    -h|--help)    usage ;;
    *) die "未知参数：$arg（使用 --help 查看用法）" ;;
  esac
done

# -----------------------------------------------------------------------------
# 读取配置
# -----------------------------------------------------------------------------
# 只填充「尚未设置」的变量：命令行环境变量 > deploy.env > 项目根 .env > 默认值
load_env_file() {
  local file="$1" line key value
  [[ -f "$file" ]] || return 0
  while IFS= read -r line || [[ -n "$line" ]]; do
    line="${line%$'\r'}"
    [[ -z "${line//[[:space:]]/}" ]] && continue
    [[ "$line" =~ ^[[:space:]]*# ]] && continue
    line="${line#export }"
    [[ "$line" == *=* ]] || continue
    key="${line%%=*}"
    value="${line#*=}"
    key="${key//[[:space:]]/}"
    [[ "$key" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || continue
    if [[ -z "${!key+x}" ]]; then
      if [[ ${#value} -ge 2 ]] && [[ "$value" == \"*\" || "$value" == \'*\' ]]; then
        value="${value:1:${#value}-2}"
      fi
      export "$key=$value"
    fi
  done < "$file"
}

if [[ ! -f "$ENV_FILE" && "$MODE" != "delete" ]]; then
  cp "$ENV_EXAMPLE" "$ENV_FILE"
  log "已生成配置文件：$ENV_FILE（可自行修改后重新执行）"
fi
load_env_file "$ENV_FILE"
load_env_file "${ROOT_DIR}/.env"

NAMESPACE="${NAMESPACE:-ruici-ai}"
APP_REPLICAS="${APP_REPLICAS:-1}"
FRONTEND_REPLICAS="${FRONTEND_REPLICAS:-2}"
IMAGE_PREFIX="${IMAGE_PREFIX:-}"
BACKEND_IMAGE_NAME="${BACKEND_IMAGE_NAME:-ruici-ai-backend}"
FRONTEND_IMAGE_NAME="${FRONTEND_IMAGE_NAME:-ruici-ai-frontend}"
IMAGE_TAG="${IMAGE_TAG:-latest}"
CONTAINER_CLI="${CONTAINER_CLI:-docker}"
PRELOAD_INFRA="${PRELOAD_INFRA:-1}"
FRONTEND_SERVICE_TYPE="${FRONTEND_SERVICE_TYPE:-NodePort}"
FRONTEND_NODE_PORT="${FRONTEND_NODE_PORT:-30080}"
INGRESS_HOST="${INGRESS_HOST:-}"
INGRESS_CLASS="${INGRESS_CLASS:-nginx}"
INGRESS_TLS_SECRET="${INGRESS_TLS_SECRET:-}"
STORAGE_CLASS="${STORAGE_CLASS:-}"
POSTGRES_SIZE="${POSTGRES_SIZE:-10Gi}"
REDIS_SIZE="${REDIS_SIZE:-2Gi}"
MINIO_SIZE="${MINIO_SIZE:-20Gi}"
POSTGRES_USER="${POSTGRES_USER:-postgres}"
POSTGRES_DB="${POSTGRES_DB:-ruici_ai_platform}"
POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-}"
APP_STORAGE_BUCKET="${APP_STORAGE_BUCKET:-ruici-ai-platform}"
APP_STORAGE_REGION="${APP_STORAGE_REGION:-us-east-1}"
APP_STORAGE_ACCESS_KEY="${APP_STORAGE_ACCESS_KEY:-minioadmin}"
APP_STORAGE_SECRET_KEY="${APP_STORAGE_SECRET_KEY:-}"
AI_DEFAULT_PROVIDER="${AI_DEFAULT_PROVIDER:-third-party}"
THIRD_PARTY_BASE_URL="${THIRD_PARTY_BASE_URL:-https://api-s.zwenooo.link/v1}"
THIRD_PARTY_API_KEY="${THIRD_PARTY_API_KEY:-}"
THIRD_PARTY_MODEL="${THIRD_PARTY_MODEL:-gpt5.2}"
THIRD_PARTY_FALLBACK_MODEL="${THIRD_PARTY_FALLBACK_MODEL:-qwen-plus}"
AI_BAILIAN_API_KEY="${AI_BAILIAN_API_KEY:-}"
AI_EMBEDDING_MODEL="${AI_EMBEDDING_MODEL:-text-embedding-v3}"
APP_VOICE_INTERVIEW_LLM_PROVIDER="${APP_VOICE_INTERVIEW_LLM_PROVIDER:-dashscope}"
OPENAI_API_KEY="${OPENAI_API_KEY:-}"
OPENAI_MODEL="${OPENAI_MODEL:-gpt-5.2}"
CORS_ALLOWED_ORIGINS="${CORS_ALLOWED_ORIGINS:-*}"
APP_VOICE_USER_UTTERANCE_DEBOUNCE_MS="${APP_VOICE_USER_UTTERANCE_DEBOUNCE_MS:-2500}"
APP_VOICE_ASR_SILENCE_MS="${APP_VOICE_ASR_SILENCE_MS:-2000}"
APP_INTERVIEW_FOLLOW_UP_COUNT="${APP_INTERVIEW_FOLLOW_UP_COUNT:-1}"
APP_INTERVIEW_EVALUATION_BATCH_SIZE="${APP_INTERVIEW_EVALUATION_BATCH_SIZE:-8}"

IMAGE_PREFIX="${IMAGE_PREFIX%/}"
if [[ -n "$IMAGE_PREFIX" ]]; then
  BACKEND_IMAGE="${IMAGE_PREFIX}/${BACKEND_IMAGE_NAME}:${IMAGE_TAG}"
  FRONTEND_IMAGE="${IMAGE_PREFIX}/${FRONTEND_IMAGE_NAME}:${IMAGE_TAG}"
  if [[ "$IMAGE_TAG" == "latest" ]]; then
    IMAGE_PULL_POLICY="Always"
  else
    IMAGE_PULL_POLICY="IfNotPresent"
  fi
else
  BACKEND_IMAGE="${BACKEND_IMAGE_NAME}:${IMAGE_TAG}"
  FRONTEND_IMAGE="${FRONTEND_IMAGE_NAME}:${IMAGE_TAG}"
  IMAGE_PULL_POLICY="IfNotPresent"
fi

rand_secret() {
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -hex 16
  else
    LC_ALL=C tr -dc 'A-Za-z0-9' </dev/urandom 2>/dev/null | head -c 32 || true
  fi
}

persist_var() {
  local key="$1" value="$2"
  if grep -qE "^[[:space:]]*${key}=" "$ENV_FILE" 2>/dev/null; then
    sed -i.bak -E "s|^[[:space:]]*${key}=.*|${key}=${value}|" "$ENV_FILE"
    rm -f "${ENV_FILE}.bak"
  else
    printf '\n%s=%s\n' "$key" "$value" >> "$ENV_FILE"
  fi
}

# 密码 / 密钥只生成一次并写回 deploy.env，避免重建后连不上已有数据卷
if [[ -z "$POSTGRES_PASSWORD" ]]; then
  POSTGRES_PASSWORD="$(rand_secret)"
  persist_var POSTGRES_PASSWORD "$POSTGRES_PASSWORD"
  log "已自动生成数据库密码并写入 deploy.env"
fi
if [[ -z "$APP_STORAGE_SECRET_KEY" ]]; then
  APP_STORAGE_SECRET_KEY="$(rand_secret)"
  persist_var APP_STORAGE_SECRET_KEY "$APP_STORAGE_SECRET_KEY"
  log "已自动生成 MinIO 密码并写入 deploy.env"
fi

# -----------------------------------------------------------------------------
# 删除模式
# -----------------------------------------------------------------------------
if [[ "$MODE" == "delete" ]]; then
  command -v kubectl >/dev/null 2>&1 || die "未找到 kubectl"
  warn "即将删除命名空间 ${NAMESPACE}，其中的 PVC 数据也会被删除！"
  kubectl delete namespace "$NAMESPACE" --ignore-not-found=true --wait=true --timeout=300s
  log "已删除命名空间 ${NAMESPACE}"
  exit 0
fi

# -----------------------------------------------------------------------------
# 前置检查
# -----------------------------------------------------------------------------
log "检查依赖与集群连接..."
command -v kubectl >/dev/null 2>&1 || die "未找到 kubectl，请先安装并配置 kubeconfig"
kubectl cluster-info >/dev/null 2>&1 || die "无法连接 Kubernetes 集群，请检查 kubeconfig / context"
log "当前 context：$(kubectl config current-context 2>/dev/null || echo unknown)"

if [[ -z "$STORAGE_CLASS" ]]; then
  default_sc="$(kubectl get storageclass -o jsonpath='{range .items[?(@.metadata.annotations.storageclass\.kubernetes\.io/is-default-class=="true")]}{.metadata.name}{"\n"}{end}' 2>/dev/null || true)"
  if [[ -z "$default_sc" ]]; then
    warn "集群没有默认 StorageClass，且 deploy.env 未设置 STORAGE_CLASS，PVC 可能一直 Pending。"
    warn "请执行 kubectl get sc 查看可用存储类，并在 deploy.env 中设置 STORAGE_CLASS=<名称>。"
  fi
fi

if [[ "$SKIP_BUILD" -eq 0 ]]; then
  command -v "$CONTAINER_CLI" >/dev/null 2>&1 \
    || die "未找到 ${CONTAINER_CLI}，可用 CONTAINER_CLI=nerdctl 指定其他构建工具，或使用 --skip-build"
  [[ -f "${ROOT_DIR}/Dockerfile" ]] || die "未找到后端 Dockerfile：${ROOT_DIR}/Dockerfile"
  [[ -f "${ROOT_DIR}/frontend/Dockerfile" ]] || die "未找到前端 Dockerfile：${ROOT_DIR}/frontend/Dockerfile"
fi

# -----------------------------------------------------------------------------
# 构建镜像
# -----------------------------------------------------------------------------
build_images() {
  log "构建后端镜像：${BACKEND_IMAGE}"
  "$CONTAINER_CLI" build -t "$BACKEND_IMAGE" -f "${ROOT_DIR}/Dockerfile" "$ROOT_DIR"
  log "构建前端镜像：${FRONTEND_IMAGE}"
  "$CONTAINER_CLI" build -t "$FRONTEND_IMAGE" -f "${ROOT_DIR}/frontend/Dockerfile" "${ROOT_DIR}/frontend"
}

# -----------------------------------------------------------------------------
# 发布镜像：有仓库则 push，否则导入本机 containerd
# -----------------------------------------------------------------------------
run_ctr() {
  if [[ "$(id -u)" -eq 0 ]]; then
    ctr "$@"
  elif sudo -n ctr version >/dev/null 2>&1; then
    sudo -n ctr "$@"
  elif command -v sudo >/dev/null 2>&1; then
    # 需要交互输入 sudo 密码
    sudo ctr "$@"
  else
    return 127
  fi
}

import_images_to_containerd() {
  local images=("$@")
  command -v ctr >/dev/null 2>&1 || {
    warn "本机未找到 ctr，无法把镜像导入 containerd。"
    warn "请二选一后重试："
    warn "  1) 在 deploy.env 中设置 IMAGE_PREFIX 指向可访问的镜像仓库；"
    warn "  2) 手动把镜像导入集群节点（docker save + sudo ctr -n k8s.io images import）。"
    die "本地镜像不可用，终止部署"
  }
  local runtime
  runtime="$(kubectl get nodes -o jsonpath='{.items[0].status.nodeInfo.containerRuntimeVersion}' 2>/dev/null || true)"
  if [[ -n "$runtime" && "$runtime" != containerd* ]]; then
    warn "集群运行时为 ${runtime}，不是 containerd，跳过导入（镜像可直接从 ${CONTAINER_CLI} 使用）。"
    return 0
  fi

  local tar
  tar="$(mktemp "${TMPDIR:-/tmp}/ruici-images.XXXXXX.tar")"
  log "导出镜像（首次约 1-3 分钟）..."
  "$CONTAINER_CLI" save -o "$tar" "${images[@]}"

  if run_ctr -n k8s.io images import "$tar"; then
    log "镜像已导入本机 containerd"
    rm -f "$tar"
  else
    warn "自动导入失败。请手动执行以下命令后重新运行："
    warn "  sudo ctr -n k8s.io images import ${tar}"
    die "镜像未导入，终止部署"
  fi

  local img
  for img in "${images[@]}"; do
    if ! run_ctr -n k8s.io images ls -q 2>/dev/null | grep -Fq "${img##*/}"; then
      warn "在 containerd 中未找到镜像 ${img}，请确认导入结果：sudo ctr -n k8s.io images ls | grep ruici"
    fi
  done
}

preload_infra_images() {
  [[ "$PRELOAD_INFRA" == "1" ]] || return 0
  command -v ctr >/dev/null 2>&1 || return 0

  local infra=(pgvector/pgvector:pg16 redis:7 minio/minio:latest minio/mc:latest)
  local missing=()
  local img
  for img in "${infra[@]}"; do
    if ! "$CONTAINER_CLI" image inspect "$img" >/dev/null 2>&1; then
      missing+=("$img")
    fi
  done
  [[ ${#missing[@]} -eq 0 ]] && return 0

  log "预拉取基础设施镜像：${missing[*]}"
  for img in "${missing[@]}"; do
    if ! "$CONTAINER_CLI" pull "$img"; then
      warn "拉取 ${img} 失败，集群节点需要能自行拉取该镜像"
      return 0
    fi
  done
  import_images_to_containerd "${missing[@]}"
}

publish_images() {
  if [[ -n "$IMAGE_PREFIX" ]]; then
    log "推送镜像到仓库：${IMAGE_PREFIX}"
    "$CONTAINER_CLI" push "$BACKEND_IMAGE"
    "$CONTAINER_CLI" push "$FRONTEND_IMAGE"
  else
    publish_local_images
  fi
}

publish_local_images() {
  local runtime
  runtime="$(kubectl get nodes -o jsonpath='{.items[0].status.nodeInfo.containerRuntimeVersion}' 2>/dev/null || true)"
  if [[ -z "$runtime" ]]; then
    warn "无法识别节点容器运行时，尝试按 containerd 处理"
  fi
  if [[ -z "$runtime" || "$runtime" == containerd* ]]; then
    import_images_to_containerd "$BACKEND_IMAGE" "$FRONTEND_IMAGE"
    warn "本地镜像模式：多节点集群请在「每个节点」执行同样的导入操作，"
    warn "或把镜像推送到所有节点都能访问的仓库（设置 IMAGE_PREFIX）。"
  else
    log "集群运行时为 ${runtime}，本机镜像可直接被节点使用"
  fi
}

if [[ "$SKIP_BUILD" -eq 0 ]]; then
  build_images
fi
publish_images
preload_infra_images

if [[ "$MODE" == "build-only" ]]; then
  log "镜像构建完成，已按要求退出（--build-only）"
  exit 0
fi

# -----------------------------------------------------------------------------
# 生成 Secret / ConfigMap
# -----------------------------------------------------------------------------
log "创建命名空间：${NAMESPACE}"
kubectl create namespace "$NAMESPACE" --dry-run=client -o yaml | kubectl apply -f - >/dev/null

log "写入应用配置（ConfigMap）与密钥（Secret）"
kubectl -n "$NAMESPACE" create configmap ruici-app-config \
  --from-literal=POSTGRES_HOST=postgres \
  --from-literal=POSTGRES_PORT=5432 \
  --from-literal=POSTGRES_USER="$POSTGRES_USER" \
  --from-literal=POSTGRES_DB="$POSTGRES_DB" \
  --from-literal=REDIS_HOST=redis \
  --from-literal=REDIS_PORT=6379 \
  --from-literal=REDIS_PASSWORD= \
  --from-literal=APP_STORAGE_ENDPOINT=http://minio:9000 \
  --from-literal=APP_STORAGE_BUCKET="$APP_STORAGE_BUCKET" \
  --from-literal=APP_STORAGE_REGION="$APP_STORAGE_REGION" \
  --from-literal=AI_DEFAULT_PROVIDER="$AI_DEFAULT_PROVIDER" \
  --from-literal=THIRD_PARTY_BASE_URL="$THIRD_PARTY_BASE_URL" \
  --from-literal=THIRD_PARTY_MODEL="$THIRD_PARTY_MODEL" \
  --from-literal=THIRD_PARTY_FALLBACK_MODEL="$THIRD_PARTY_FALLBACK_MODEL" \
  --from-literal=AI_EMBEDDING_MODEL="$AI_EMBEDDING_MODEL" \
  --from-literal=OPENAI_MODEL="$OPENAI_MODEL" \
  --from-literal=APP_VOICE_INTERVIEW_LLM_PROVIDER="$APP_VOICE_INTERVIEW_LLM_PROVIDER" \
  --from-literal=APP_VOICE_USER_UTTERANCE_DEBOUNCE_MS="$APP_VOICE_USER_UTTERANCE_DEBOUNCE_MS" \
  --from-literal=APP_VOICE_ASR_SILENCE_MS="$APP_VOICE_ASR_SILENCE_MS" \
  --from-literal=APP_INTERVIEW_FOLLOW_UP_COUNT="$APP_INTERVIEW_FOLLOW_UP_COUNT" \
  --from-literal=APP_INTERVIEW_EVALUATION_BATCH_SIZE="$APP_INTERVIEW_EVALUATION_BATCH_SIZE" \
  --from-literal=CORS_ALLOWED_ORIGINS="$CORS_ALLOWED_ORIGINS" \
  --from-literal=MANAGEMENT_ENDPOINT_HEALTH_PROBES_ENABLED=true \
  --from-literal=SERVER_FORWARD_HEADERS_STRATEGY=framework \
  --from-literal=TZ=Asia/Shanghai \
  --dry-run=client -o yaml | kubectl apply -f - >/dev/null

kubectl -n "$NAMESPACE" create secret generic ruici-app-secret \
  --from-literal=POSTGRES_PASSWORD="$POSTGRES_PASSWORD" \
  --from-literal=THIRD_PARTY_API_KEY="$THIRD_PARTY_API_KEY" \
  --from-literal=AI_BAILIAN_API_KEY="$AI_BAILIAN_API_KEY" \
  --from-literal=OPENAI_API_KEY="$OPENAI_API_KEY" \
  --from-literal=APP_STORAGE_ACCESS_KEY="$APP_STORAGE_ACCESS_KEY" \
  --from-literal=APP_STORAGE_SECRET_KEY="$APP_STORAGE_SECRET_KEY" \
  --from-literal=MINIO_ROOT_USER="$APP_STORAGE_ACCESS_KEY" \
  --from-literal=MINIO_ROOT_PASSWORD="$APP_STORAGE_SECRET_KEY" \
  --dry-run=client -o yaml | kubectl apply -f - >/dev/null

kubectl -n "$NAMESPACE" create configmap postgres-init \
  --from-file=init.sql="${SCRIPT_DIR}/postgres-init.sql" \
  --dry-run=client -o yaml | kubectl apply -f - >/dev/null

kubectl -n "$NAMESPACE" create configmap frontend-nginx \
  --from-file=default.conf="${SCRIPT_DIR}/frontend-nginx.conf" \
  --dry-run=client -o yaml | kubectl apply -f - >/dev/null

if [[ -z "$THIRD_PARTY_API_KEY" && -z "$AI_BAILIAN_API_KEY" ]]; then
  warn "未配置 AI Key（THIRD_PARTY_API_KEY / AI_BAILIAN_API_KEY），应用可启动但 AI 功能会报错。"
  warn "请编辑 ${ENV_FILE} 后重新执行 ./deploy.sh --skip-build"
fi

# -----------------------------------------------------------------------------
# 渲染并应用工作负载
# -----------------------------------------------------------------------------
render_manifests() {
  rm -rf "$RENDER_DIR"
  mkdir -p "$RENDER_DIR"

  local storage_class_line=""
  [[ -n "$STORAGE_CLASS" ]] && storage_class_line="storageClassName: ${STORAGE_CLASS}"

  local node_port_line=""
  if [[ "$FRONTEND_SERVICE_TYPE" == "NodePort" && -n "$FRONTEND_NODE_PORT" ]]; then
    node_port_line="nodePort: ${FRONTEND_NODE_PORT}"
  fi

  local deploy_ts
  deploy_ts="$(date +%s)"

  local src base
  for src in "${MANIFEST_DIR}"/*.yaml; do
    base="$(basename "$src")"
    if [[ "$base" == "60-ingress.yaml" && -z "$INGRESS_HOST" ]]; then
      continue
    fi
    sed \
      -e "s|__BACKEND_IMAGE__|${BACKEND_IMAGE}|g" \
      -e "s|__FRONTEND_IMAGE__|${FRONTEND_IMAGE}|g" \
      -e "s|__IMAGE_PULL_POLICY__|${IMAGE_PULL_POLICY}|g" \
      -e "s|__APP_REPLICAS__|${APP_REPLICAS}|g" \
      -e "s|__FRONTEND_REPLICAS__|${FRONTEND_REPLICAS}|g" \
      -e "s|__FRONTEND_SERVICE_TYPE__|${FRONTEND_SERVICE_TYPE}|g" \
      -e "s|__NODE_PORT_LINE__|${node_port_line}|g" \
      -e "s|__STORAGE_CLASS_LINE__|${storage_class_line}|g" \
      -e "s|__POSTGRES_SIZE__|${POSTGRES_SIZE}|g" \
      -e "s|__REDIS_SIZE__|${REDIS_SIZE}|g" \
      -e "s|__MINIO_SIZE__|${MINIO_SIZE}|g" \
      -e "s|__DEPLOY_TS__|${deploy_ts}|g" \
      -e "s|__INGRESS_HOST__|${INGRESS_HOST}|g" \
      -e "s|__INGRESS_CLASS__|${INGRESS_CLASS}|g" \
      "$src" > "${RENDER_DIR}/${base}"
  done

  local ingress_file="${RENDER_DIR}/60-ingress.yaml"
  if [[ -n "$INGRESS_HOST" && -f "$ingress_file" ]]; then
    local tls_block=""
    if [[ -n "$INGRESS_TLS_SECRET" ]]; then
      tls_block="  tls:
    - hosts:
        - ${INGRESS_HOST}
      secretName: ${INGRESS_TLS_SECRET}"
    fi
    awk -v block="$tls_block" '
      /__INGRESS_TLS_BLOCK__/ {
        if (block != "") print block;
        next
      }
      { print }
    ' "$ingress_file" > "${ingress_file}.tmp"
    mv "${ingress_file}.tmp" "$ingress_file"
  fi
}

log "渲染部署清单"
render_manifests

log "部署基础设施：PostgreSQL / Redis / MinIO"
kubectl apply -f "${RENDER_DIR}/10-postgres.yaml"
kubectl apply -f "${RENDER_DIR}/20-redis.yaml"
kubectl apply -f "${RENDER_DIR}/30-minio.yaml"

log "等待 PostgreSQL 就绪..."
kubectl -n "$NAMESPACE" rollout status deployment/postgres --timeout=300s
log "等待 Redis 就绪..."
kubectl -n "$NAMESPACE" rollout status deployment/redis --timeout=180s
log "等待 MinIO 就绪..."
kubectl -n "$NAMESPACE" rollout status deployment/minio --timeout=300s

log "部署后端与前端"
kubectl apply -f "${RENDER_DIR}/40-backend.yaml"
kubectl apply -f "${RENDER_DIR}/50-frontend.yaml"
if [[ -f "${RENDER_DIR}/60-ingress.yaml" ]]; then
  kubectl apply -f "${RENDER_DIR}/60-ingress.yaml"
fi

if ! kubectl -n "$NAMESPACE" rollout status deployment/app --timeout=900s; then
  warn "后端未能在 15 分钟内就绪，请查看日志："
  warn "  kubectl -n ${NAMESPACE} get pods"
  warn "  kubectl -n ${NAMESPACE} logs deployment/app --tail=100"
  exit 1
fi
kubectl -n "$NAMESPACE" rollout status deployment/frontend --timeout=300s

# -----------------------------------------------------------------------------
# 输出访问地址
# -----------------------------------------------------------------------------
print_access() {
  echo
  log "部署完成。资源清单："
  kubectl -n "$NAMESPACE" get deploy,svc,pvc -o wide 2>/dev/null || true

  echo
  case "$FRONTEND_SERVICE_TYPE" in
    NodePort)
      local port node_ip
      port="$(kubectl -n "$NAMESPACE" get svc frontend -o jsonpath='{.spec.ports[0].nodePort}')"
      node_ip="$(kubectl get nodes -o jsonpath='{.items[0].status.addresses[?(@.type=="InternalIP")].address}' 2>/dev/null || true)"
      log "访问地址：http://${node_ip:-<NodeIP>}:${port}"
      ;;
    LoadBalancer)
      local lb
      lb="$(kubectl -n "$NAMESPACE" get svc frontend -o jsonpath='{.status.loadBalancer.ingress[0].ip}{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null || true)"
      if [[ -n "$lb" ]]; then
        log "访问地址：http://${lb}"
      else
        warn "LoadBalancer 正在分配地址，稍后用 kubectl -n ${NAMESPACE} get svc frontend 查看"
      fi
      ;;
    *)
      log "Service 类型为 ${FRONTEND_SERVICE_TYPE}，本地访问可执行："
      log "  kubectl -n ${NAMESPACE} port-forward svc/frontend 8080:80"
      log "  然后打开 http://127.0.0.1:8080"
      ;;
  esac

  if [[ -n "$INGRESS_HOST" ]]; then
    if [[ -n "$INGRESS_TLS_SECRET" ]]; then
      log "Ingress 地址：https://${INGRESS_HOST}"
    else
      log "Ingress 地址：http://${INGRESS_HOST}"
    fi
  fi
  echo
  log "提示：AI Key 变更后重新执行 ./deploy.sh --skip-build 即可滚动更新。"
}

print_access