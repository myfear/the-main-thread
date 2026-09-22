INSERT INTO author(id, firstname, lastname) VALUES (1, 'Jo', 'Nesbø');
INSERT INTO author(id, firstname, lastname) VALUES (2, 'John', 'Irving');
ALTER SEQUENCE author_seq RESTART WITH 3;

INSERT INTO book(id, title, author_id) VALUES (1, 'The Snowman', 1);
INSERT INTO book(id, title, author_id) VALUES (2, 'The Bat', 1);
INSERT INTO book(id, title, author_id) VALUES (3, 'The Redeemer', 1);
INSERT INTO book(id, title, author_id) VALUES (4, 'A Prayer for Owen Meany', 2);
ALTER SEQUENCE book_seq RESTART WITH 5;
