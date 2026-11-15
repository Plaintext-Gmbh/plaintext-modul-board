-- Karte 2193: Beschreibungen aus dem Nextcloud-Deck koennen >100k Zeichen sein
-- (gemessen: bis 235 KB). varchar(100000) schneidet sie beim Import ab -> text.
ALTER TABLE board_card ALTER COLUMN description TYPE text;
