# 家電・自動車用鋼板 受発注・在庫引当システム

Spring Boot 3.5.16、Java 21、Thymeleaf、Spring Security、Spring Data JPA を使用した、鋼板の受発注・在庫引当・出荷管理システムです。

## 現在の実装状況

### 実装済み

- Spring Security によるフォームログイン認証・ロールベースアクセス制御
- ダッシュボード（`GET /`）
  - 本日の受注件数、引当待ち件数、出荷待ち件数のKPI表示
  - 有効在庫ゼロ、安全在庫割れ、バックオーダー、納期アラート、長期滞留コイルの表示
- 在庫照会・検索（`GET /inventories`）
  - 鋼種、用途区分、板厚、板幅による絞り込み
  - 鋼板別の物理残重量、引当済重量、有効在庫重量を集計
  - コイルを入荷日昇順で表示し、コイルステータスと長期滞留を表示
- 受注登録・注文一覧（`GET /orders`、`POST /orders`、`POST /orders/{id}/cancel`）
  - 在庫引当（FIFO）、引当不足時のバックオーダー登録
  - 楽観的排他制御による同時更新競合の検知
  - キャンセル時の引当解除・在庫戻し
- コイル入荷登録（`GET /arrivals`、`POST /arrivals`）
  - 入荷履歴表示、バックオーダーのFIFO自動充当
- 出荷管理・帳票出力（`GET /shipments`、`POST /shipments/{id}/confirm`、`GET /shipments/{id}/mill-sheet.pdf`、`GET /shipments/export.csv`）
  - 出荷確定時の実在庫（`physical_weight`）とコイル引当量の更新
  - ミルシートPDF生成（OpenPDF）
  - 出荷実績CSV出力（OpenCSV、UTF-8 BOM付き）
- Bootstrap 5.3 共通レイアウトとサイドバー
- H2 インメモリデータベースによる初期スキーマ・サンプルデータ投入
- 外部連携用REST APIとSwagger UI

## 技術スタック

- Java 21
- Spring Boot 3.5.16
- Spring MVC / Thymeleaf
- Spring Security 6
- Spring Data JPA / Hibernate 6
- H2 Database（PostgreSQL互換モード）
- Bootstrap 5.3 / Bootstrap Icons
- Lombok
- Springdoc OpenAPI / Swagger UI
- OpenPDF / OpenCSV
- JUnit 5 / Spring Boot Test / MockMvc / Mockito
- Maven

## 主な画面

| 画面 | URL | 概要 |
|---|---|---|
| ログイン | `/login` | ユーザー認証 |
| ダッシュボード | `/` | KPIと要対応アラート |
| 在庫照会・検索 | `/inventories` | 鋼板・コイル在庫検索 |
| 受注登録・注文一覧 | `/orders` | 注文登録・一覧・キャンセル |
| コイル入荷登録 | `/arrivals` | 入荷登録・バックオーダー充当 |
| 出荷管理・帳票出力 | `/shipments` | 出荷確定・PDF・CSV出力 |

## デモユーザー

すべてのデモアカウントのパスワードは `password` です。

| ログインID | 表示名 | ロール |
|---|---|---|
| `sales01` | 営業 一郎 | `ROLE_SALES` |
| `warehouse01` | 倉庫 二郎 | `ROLE_WAREHOUSE` |
| `admin` | システム管理 三郎 | `ROLE_ADMIN` |

## 画面・操作権限

| 操作 | 営業担当（SALES） | 倉庫担当（WAREHOUSE） | 管理者（ADMIN） |
|---|:---:|:---:|:---:|
| ダッシュボード・在庫照会・注文一覧・入荷履歴・出荷一覧の閲覧 | ○ | ○ | ○ |
| 注文登録・注文キャンセル | ○ | × | ○ |
| 入荷登録・出荷確定 | × | ○ | ○ |
| PDF・CSVの出力 | ○ | ○ | ○ |

※画面アクセス自体は認証済みユーザーなら可能です。更新操作は上記ロール制限をSpring Securityで適用します。

## 必要環境

- macOS / Windows / Linux
- JDK 21
- Maven 3.9以上

## 起動方法

プロジェクトルートへ移動します。

```bash
cd steel-order-system
```

macOSでJava 21を明示する場合：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v21)
```

Maven経由で起動する場合：

```bash
mvn clean spring-boot:run
```

実行可能JARを利用する場合：

```bash
mvn clean package -DskipTests
java -jar target/steel-order-system-1.0.0.jar
```

起動後は <http://localhost:8080/login> にアクセスしてください。

## 開発者向けURL

| 用途 | URL |
|---|---|
| ダッシュボード | <http://localhost:8080/> |
| H2 Console | <http://localhost:8080/h2-console> |
| Swagger UI | <http://localhost:8080/swagger-ui.html> |
| 外部連携エンドポイント | <http://localhost:8080/api/v1/inventories>、<http://localhost:8080/api/v1/orders>、<http://localhost:8080/api/v1/arrivals> |

H2 Console設定例：

- JDBC URL：`jdbc:h2:mem:steeldb`
- ユーザー名：`sa`
- パスワード：空欄

## データベース初期化

- `src/main/resources/schema.sql`
- `src/main/resources/data.sql`

起動時にスキーマとサンプルデータを自動投入します。H2はインメモリデータベースなので、アプリケーション終了後のデータは保持されません。

## テスト

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v21)
mvn test
```

JaCoCoのカバレッジレポートを出力する場合：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v21)
mvn clean test jacoco:report
```

レポート出力先：`target/site/jacoco/index.html`

macOSでレポートを開く例：

```bash
open target/site/jacoco/index.html
```

### JaCoCo カバレッジ実績（2026-11-24）

現在の実績値です。

| 対象 | 現在値 |
|---|---:|
| 全体（Instruction） | 86% |
| 全体（Branch） | 67% |
| service（Instruction） | 84% |
| service（Branch） | 68% |
| controller（Instruction） | 85% |
| controller（Branch） | 75% |
| dto（Instruction） | 92% |
| dto（Branch） | 25% |

計測コマンド：

```bash
mvn clean test jacoco:report
```

## プロジェクト構成

```text
src/main/java/com/example/steelorder
├── config/          Spring Security設定
├── controller/      画面コントローラー
├── dto/             画面表示用record DTO
├── entity/          JPAエンティティ・ステータスEnum
├── repository/      Spring Data JPA Repository
└── service/         業務ロジック・認証サービス

src/main/resources
├── application.yml
├── schema.sql
├── data.sql
└── templates/       Thymeleafテンプレート

docs/                 設計ドキュメント
```

## ダッシュボードアラート

- `DANGER`：有効在庫ゼロ、バックオーダー滞留、本日納期の未出荷
- `WARNING`：安全在庫割れ、納期接近の未出荷
- `INFO`：入荷から90日以上経過したコイル

緊急度の表示色はBootstrapの `alert-danger`、`alert-warning`、`alert-info`を使用します。

## 外部連携REST API

Swagger UI（<http://localhost:8080/swagger-ui.html>）からAPI仕様を確認できます。

| メソッド | URL | 概要 |
|---|---|---|
| `GET` | `/api/v1/inventories` | 鋼板別在庫サマリーとコイル明細を取得 |
| `GET` | `/api/v1/orders` | 全注文と引当明細を取得 |
| `POST` | `/api/v1/orders` | 注文を登録しFIFO自動引当を実行 |
| `POST` | `/api/v1/arrivals` | コイル入荷を登録しバックオーダーを自動充当 |

REST APIはBean Validationで検証されます。`/api/v1/inventories` は `@Valid @ModelAttribute` によるクエリパラメータ検証（`@DecimalMin`、`@Positive`）を行い、`/api/v1/orders` と `/api/v1/arrivals` は `@RequestBody @Valid` によるリクエストボディ検証を行います。画面と同様に、Spring SecurityおよびCSRF設定の対象です。
