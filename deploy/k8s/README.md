# Kubernetes 一键部署

把项目目录拷到任意一台能执行 `kubectl` 的机器（最推荐直接放在 k8s 节点上），执行脚本即可完成：

- 构建后端 / 前端镜像
- 部署 PostgreSQL 16 + pgvector、Redis 7、MinIO
- 部署 Spring Boot 后端、React 前端（Nginx 反向代理 `/api`、`/ws`）
- 自动建库、建 bucket、生成并保存随机密码
- 等待全部工作负载就绪并输出访问地址

## 一、前提条件

| 依赖 | 说明 |
|------|------|
| Kubernetes | ≥ 1.24（已在 1.28 验证），有可用的默认 StorageClass |
| kubectl | 本机可正常访问集群（`kubectl get nodes` 有输出） |
| Bash | Linux / macOS / Git Bash 均可 |
| docker | 用于构建镜像；也可用 `CONTAINER_CLI=nerdctl` |
| 网络 | 构建机需要能访问 Maven Central / npm；集群节点需要能拉取 `pgvector`、`redis`、`minio`、`mc` 镜像（脚本可帮忙预导入，见下文） |

> 如果没有默认 StorageClass，请在 `deploy.env` 中显式设置 `STORAGE_CLASS=你的存储类`。

## 二、快速开始

```bash
# 1. 进入部署目录
cd deploy/k8s

# 2. 首次运行会自动生成 deploy.env，先填入 AI Key
vi deploy.env          # 至少填写 THIRD_PARTY_API_KEY、AI_BAILIAN_API_KEY

# 3. 一键构建 + 部署
bash deploy.sh
```

看到下面的输出即部署成功：

```
[ruici] 访问地址：http://<节点IP>:30080
```

之后改代码或改配置，重复执行即可：

```bash
bash deploy.sh --skip-build     # 不重新构建镜像，只重新下发配置
bash deploy.sh                  # 重新构建 + 滚动更新
```

## 三、deploy.env 关键配置

完整字段见 `deploy.env.example`，最常用的几项：

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `NAMESPACE` | `ruici-ai` | 资源所在命名空间 |
| `IMAGE_PREFIX` | 空 | 空 = 本地镜像模式；填写仓库地址（如 `192.168.233.129:30002/library`）则推送镜像 |
| `IMAGE_TAG` | `latest` | 镜像标签 |
| `FRONTEND_SERVICE_TYPE` | `NodePort` | `NodePort` / `ClusterIP` / `LoadBalancer` |
| `FRONTEND_NODE_PORT` | `30080` | 留空则自动分配 |
| `STORAGE_CLASS` | 空 | 留空使用集群默认存储类 |
| `POSTGRES_PASSWORD` | 自动生成 | 首次部署写回 `deploy.env`，**之后不要再改** |
| `APP_STORAGE_SECRET_KEY` | 自动生成 | MinIO 密码，首次部署写回 `deploy.env` |
| `THIRD_PARTY_API_KEY` | 空 | 主聊天模型（必需，否则 AI 功能报错） |
| `AI_BAILIAN_API_KEY` | 空 | 向量化 + 语音（必需） |
| `INGRESS_HOST` | 空 | 填写域名后自动创建 Ingress |

> `deploy.env` 含密钥，已在 `.gitignore` 中忽略，请勿提交。

## 四、两种镜像模式

### 1. 本地镜像（默认，适合单节点 / 小集群）

`IMAGE_PREFIX` 留空时，脚本会：

1. 用 docker 构建镜像；
2. `docker save` 后导入本机 containerd（`k8s.io` 命名空间），k8s 直接使用，无需镜像仓库；
3. 同时尝试预拉取 `pgvector`、`redis`、`minio`、`mc` 并一并导入。

**多节点集群注意**：本地导入只对「运行脚本的那台机器」生效。请选择其一：

- 在每个节点上都执行一次脚本的镜像导入（脚本结束时会提示具体命令）；
- 或者配置 `IMAGE_PREFIX` 指向 Harbor 等所有节点都能访问的仓库，脚本会自动 `docker push`。

### 2. 镜像仓库模式

```bash
IMAGE_PREFIX=192.168.233.129:30002/library
IMAGE_TAG=1.0.0
```

集群节点必须能拉取该仓库；Harbor 若使用 HTTP/自签证书，需要提前配置 containerd 的 `insecure-registries`。

## 五、部署了什么

| 资源 | 名称 | 说明 |
|------|------|------|
| Namespace | `ruici-ai` | 可用 `NAMESPACE` 修改 |
| Deployment/Service | `postgres` | pgvector，`postgres-init` ConfigMap 初始化 `vector` 扩展 |
| Deployment/Service | `redis` | 开启 AOF，承载缓存与 Redis Stream 异步任务 |
| Deployment/Service | `minio` | 对象存储；后端启动前自动创建 bucket |
| Deployment/Service | `app` | Spring Boot 后端，健康检查 `/actuator/health/*` |
| Deployment/Service | `frontend` | Nginx，代理 `/api`（SSE 不缓冲）与 `/ws`（语音 WebSocket） |
| ConfigMap | `ruici-app-config` / `frontend-nginx` / `postgres-init` | 非敏感配置 |
| Secret | `ruici-app-secret` | 数据库密码、AI Key、MinIO 账号密码 |
| PVC | `postgres-data` / `redis-data` / `minio-data` | 数据持久化 |

## 六、常用运维命令

```bash
kubectl -n ruici-ai get pods -w
kubectl -n ruici-ai logs deployment/app -f
kubectl -n ruici-ai port-forward svc/minio 9001:9001   # MinIO 控制台 http://127.0.0.1:9001
kubectl -n ruici-ai port-forward svc/app   8080:8080    # 直连后端 / Swagger
```

卸载（**会删除 PVC 数据**）：

```bash
bash deploy.sh --delete
```

## 七、常见问题

### 1. Pod 一直 Pending，PVC 未绑定

```bash
kubectl -n ruici-ai describe pvc postgres-data
```

多数是集群没有默认 StorageClass：`kubectl get sc`，然后在 `deploy.env` 中填写 `STORAGE_CLASS`。

### 2. ImagePullBackOff

- 本地镜像模式：确认镜像已导入运行该 Pod 的节点（脚本只导入本机）；
- 仓库模式：确认节点能访问仓库、仓库地址与 tag 正确；
- 基础设施镜像拉取失败：在能访问 Docker Hub 的机器上设置 `PRELOAD_INFRA=1` 重新执行，或给节点配置代理。

### 3. 后端启动失败 / CrashLoopBackOff

```bash
kubectl -n ruici-ai logs deployment/app --tail=200
```

常见原因：

- 数据库未就绪：查看 `postgres` Pod 状态；初始化脚本只会在数据卷为空时执行；
- AI Key 错误：`401` / `invalid api key`，修改 `deploy.env` 后 `bash deploy.sh --skip-build`；
- 内存不足：调大 `40-backend.yaml` 中的 `resources.limits.memory`。

### 4. 语音通话连不上 WebSocket

本仓库的 k8s Nginx 配置已补齐 `/ws/` 代理与 `Upgrade` 头，并透传 `Host`（含端口）与 `X-Forwarded-Proto`，后端才能生成正确的 `ws://` / `wss://` 地址。

如果前面还有云厂商 SLB / 其他 Ingress，请确认也开启了 WebSocket 支持。

### 5. 改了数据库密码导致连不上

`POSTGRES_PASSWORD` 只在首次初始化时写入数据卷。数据卷已存在时修改 secret 只会改应用配置，数据库密码不会跟着变。恢复方式：改回原密码，或进入 postgres 容器执行 `ALTER USER`。

### 6. 想换后端副本数 / 前端副本数

```bash
APP_REPLICAS=2 FRONTEND_REPLICAS=3 bash deploy.sh --skip-build
```

后端语音会话依赖单实例内存状态，默认保持 1 副本；如需扩容，请配合会话粘滞（Ingress `session-affinity`）或先改造会话存储。

## 八、目录结构

```
deploy/k8s/
├── deploy.sh              # 一键部署脚本
├── deploy.env.example     # 配置模板（复制为 deploy.env）
├── frontend-nginx.conf    # k8s 版前端 Nginx 配置（/api、/ws 代理）
├── postgres-init.sql      # 数据库初始化（创建 vector 扩展）
├── README.md
└── manifests/             # Kubernetes 清单模板（由脚本渲染后应用）
    ├── 10-postgres.yaml
    ├── 20-redis.yaml
    ├── 30-minio.yaml
    ├── 40-backend.yaml
    ├── 50-frontend.yaml
    └── 60-ingress.yaml
```