-- 1. ユーザーマスタ（パスワードはすべて "password" をBCryptハッシュ化した値）
INSERT INTO users (username, password, display_name, role) VALUES
('sales01', '$2a$10$eInKmwYEztC5yeIFCGkVm.RhGZJkrj6D9aGL4xW7mjpX8CptIfRFu', '営業 一郎', 'ROLE_SALES'),
('warehouse01', '$2a$10$eInKmwYEztC5yeIFCGkVm.RhGZJkrj6D9aGL4xW7mjpX8CptIfRFu', '倉庫 二郎', 'ROLE_WAREHOUSE'),
('admin', '$2a$10$eInKmwYEztC5yeIFCGkVm.RhGZJkrj6D9aGL4xW7mjpX8CptIfRFu', 'システム管理 三郎', 'ROLE_ADMIN');

-- 2. 鋼板マスタ（家電・自動車用鋼板 4種類）
INSERT INTO steel_sheets (steel_code, steel_name, steel_grade, usage_category, thickness, width, safety_stock_weight) VALUES
('STL-SPCC-01', '冷延鋼板（SPCC）', 'SPCC', '家電筐体・事務機器用', 1.00, 1000, 10000),
('STL-SGCC-01', '溶融亜鉛めっき鋼板（SGCC）', 'SGCC', '冷蔵庫・洗濯機外板用', 0.80, 1219, 10000),
('STL-JAC270-01', '自動車用高張力鋼板（ハイテン材）', 'JAC270C', '自動車ボディ骨格用', 1.40, 1500, 15000),
('STL-SECC-01', '電気亜鉛めっき鋼板（SECC）', 'SECC', '車載電装部品・AV機器用', 1.20, 914, 8000);

-- 3. コイル在庫テーブル（各鋼板の在庫状況にバリエーションを持たせる）
-- [鋼板1: SPCC] 有効在庫 14,000kg（安全在庫10,000kg以上 → ステータス: 適正）
INSERT INTO coils (coil_number, steel_sheet_id, initial_weight, physical_weight, available_weight, status, arrival_date, version) VALUES
('CL-202611-001', 1, 10000, 10000, 4000, 'PARTIALLY_ALLOCATED', '2026-11-05', 1), -- 6,000kg引当済
('CL-202611-002', 1, 10000, 10000, 10000, 'AVAILABLE', '2026-11-12', 0);

-- [鋼板2: SGCC] 有効在庫 4,500kg（安全在庫10,000kg割れ → 黄色アラート点灯！）
INSERT INTO coils (coil_number, steel_sheet_id, initial_weight, physical_weight, available_weight, status, arrival_date, version) VALUES
('CL-202611-003', 2, 8500, 8500, 4500, 'PARTIALLY_ALLOCATED', '2026-11-08', 1); -- 4,000kg引当済

-- [鋼板3: JAC270ハイテン材] 有効在庫 0kg（全量引当済＋バックオーダーあり → 赤色緊急アラート点灯！）
INSERT INTO coils (coil_number, steel_sheet_id, initial_weight, physical_weight, available_weight, status, arrival_date, version) VALUES
('CL-202611-004', 3, 12000, 12000, 0, 'FULLY_ALLOCATED', '2026-11-10', 1); -- 12,000kg全量引当済

-- [鋼板4: SECC] 長期滞留コイル（95日経過 → 青色注意アラート点灯！）
INSERT INTO coils (coil_number, steel_sheet_id, initial_weight, physical_weight, available_weight, status, arrival_date, version) VALUES
('CL-202608-005', 4, 9000, 9000, 9000, 'AVAILABLE', '2026-08-18', 0);

-- 4. 注文テーブル（引当済・納期切迫・バックオーダー・出荷済を再現）
-- ① 引当済 ＆ 本日納期（出荷管理画面のデモ・赤色納期アラート用）
INSERT INTO orders (order_number, customer_name, steel_sheet_id, order_weight, allocated_weight, desired_delivery_date, status, ordered_at, shipped_at) VALUES
('ORD-202611-001', 'テスト家電製造株式会社', 1, 6000, 6000, '2026-11-21', 'ALLOCATED', '2026-11-15 10:00:00', NULL);

-- ② 引当済 ＆ 納期接近（2日後納期・黄色アラート用）
INSERT INTO orders (order_number, customer_name, steel_sheet_id, order_weight, allocated_weight, desired_delivery_date, status, ordered_at, shipped_at) VALUES
('ORD-202611-002', 'テストエレクトロニクス株式会社', 2, 4000, 4000, '2026-11-23', 'ALLOCATED', '2026-11-16 14:30:00', NULL);

-- ③ 引当済（ハイテン材の在庫12,000kgを使い切っている注文）
INSERT INTO orders (order_number, customer_name, steel_sheet_id, order_weight, allocated_weight, desired_delivery_date, status, ordered_at, shipped_at) VALUES
('ORD-202611-003', 'テスト自動車ボディ株式会社', 3, 12000, 12000, '2026-11-28', 'ALLOCATED', '2026-11-17 09:15:00', NULL);

-- ④ ★バックオーダー（引当待ち・赤色緊急アラート用）
-- 入荷登録画面で「JAC270ハイテン材」を入庫すると、この注文へ自動で引き当てられる！
INSERT INTO orders (order_number, customer_name, steel_sheet_id, order_weight, allocated_weight, desired_delivery_date, status, ordered_at, shipped_at) VALUES
('ORD-202611-004', 'テストオートパーツ株式会社', 3, 8000, 0, '2026-11-30', 'BACKORDERED', '2026-11-18 16:45:00', NULL);

-- ⑤ 出荷済（出荷履歴CSV出力・ミルシートPDF再発行のデモ用）
INSERT INTO orders (order_number, customer_name, steel_sheet_id, order_weight, allocated_weight, desired_delivery_date, status, ordered_at, shipped_at) VALUES
('ORD-202611-000', 'テスト精密機器株式会社', 1, 5000, 5000, '2026-11-18', 'SHIPPED', '2026-11-10 11:00:00', '2026-11-18 15:20:00');

-- 5. 引当明細テーブル（どのコイルから引き当てられているかの紐づけ）
INSERT INTO order_allocations (order_id, coil_id, allocated_weight, allocated_at) VALUES
(1, 1, 6000, '2026-11-15 10:00:05'), -- ORD-001 は CL-001 から 6,000kg 引当
(2, 3, 4000, '2026-11-16 14:30:05'), -- ORD-002 は CL-003 から 4,000kg 引当
(3, 4, 12000, '2026-11-17 09:15:05'); -- ORD-003 は CL-004 から 12,000kg 引当

-- 6. 入荷履歴テーブル（初期在庫コイルに対応する過去の入庫記録）
INSERT INTO arrival_histories (coil_id, steel_sheet_id, arrival_weight, arrival_date, auto_allocated_info, created_at) VALUES
(1, 1, 10000, '2026-11-05', NULL, '2026-11-05 09:00:00'),
(2, 1, 10000, '2026-11-12', NULL, '2026-11-12 10:30:00'),
(3, 2, 8500, '2026-11-08', NULL, '2026-11-08 13:15:00'),
(4, 3, 12000, '2026-11-10', NULL, '2026-11-10 11:00:00'),
(5, 4, 9000, '2026-08-18', NULL, '2026-08-18 14:00:00');
