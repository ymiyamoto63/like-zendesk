-- 性能確認用の大量データ（devtools.app.LoadData）を SQL でまとめて書いた直後に、API の接続から ANALYZE できるようにする。
-- 統計が古いままだと、自動 ANALYZE が走るまでチケット一覧が順次走査になり、数十秒かかる。
GRANT MAINTAIN ON tickets, comments TO app_runtime;
