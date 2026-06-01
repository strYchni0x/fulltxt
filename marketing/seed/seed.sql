-- Testdaten für Play-Store-Screenshots (keine echten Daten).
-- Wird in eine vom App-Code erzeugte fulltxt.db eingespielt (Schema bleibt unangetastet).
-- file_metadata.cloudProvider trägt den Enum-Namen als String.

DELETE FROM file_metadata;
DELETE FROM file_content_fts;

-- ============ GOOGLE DRIVE (gd) ============
INSERT INTO file_metadata (fileId,fileName,cloudPath,cloudProvider,accountId,fileSizeBytes,createdAt,modifiedAt,mimeType,changeToken,checksum,indexedAt,webUrl) VALUES
('gd-1','Rechnung_2026_0042.pdf','/1A2b3C/Rechnung_2026_0042.pdf','GOOGLE_DRIVE','gd',184320,1746000000000,1748600000000,'application/pdf',NULL,NULL,1748600500000,NULL),
('gd-2','Projektplan_Q2.docx','/1A2b3C/Projektplan_Q2.docx','GOOGLE_DRIVE','gd',46211,1744000000000,1748100000000,'application/vnd.openxmlformats-officedocument.wordprocessingml.document',NULL,NULL,1748600500000,NULL),
('gd-3','Urlaubsantrag_Mai.pdf','/1A2b3C/Urlaubsantrag_Mai.pdf','GOOGLE_DRIVE','gd',88112,1745200000000,1746800000000,'application/pdf',NULL,NULL,1748600500000,NULL),
('gd-4','Praesentation_Kickoff.pptx','/1A2b3C/Praesentation_Kickoff.pptx','GOOGLE_DRIVE','gd',1330221,1743000000000,1747900000000,'application/vnd.openxmlformats-officedocument.presentationml.presentation',NULL,NULL,1748600500000,NULL);

INSERT INTO file_content_fts (fileId,fileName,content) VALUES
('gd-1','Rechnung_2026_0042.pdf','Rechnung Nr. 2026-0042 vom 12. Mai 2026. Sehr geehrte Damen und Herren, wir erlauben uns, Ihnen die folgenden Leistungen in Rechnung zu stellen. Rechnungsbetrag gesamt: 1.248,00 EUR inklusive Mehrwertsteuer. Zahlbar innerhalb von 14 Tagen ohne Abzug.'),
('gd-2','Projektplan_Q2.docx','Projektplan für das zweite Quartal. Das Projekt umfasst drei Arbeitspakete mit klaren Meilensteinen. Verantwortlich für die Koordination ist das Projektbüro. Der Projektabschluss ist für Ende Juni geplant.'),
('gd-3','Urlaubsantrag_Mai.pdf','Urlaubsantrag für den Zeitraum vom 19. bis 30. Mai 2026. Der Resturlaub aus dem Vorjahr beträgt fünf Tage. Bitte um Genehmigung durch die Abteilungsleitung.'),
('gd-4','Praesentation_Kickoff.pptx','Kickoff-Präsentation zum neuen Projekt. Agenda: Zielsetzung, Zeitplan, Budget und nächste Schritte. Das Projekt startet offiziell im Mai und läuft über sechs Monate.');

-- ============ ONEDRIVE (od) ============
INSERT INTO file_metadata (fileId,fileName,cloudPath,cloudProvider,accountId,fileSizeBytes,createdAt,modifiedAt,mimeType,changeToken,checksum,indexedAt,webUrl) VALUES
('od-1','Mietvertrag_Wohnung.pdf','/Dokumente/Wohnung/Mietvertrag_Wohnung.pdf','ONE_DRIVE','od',265114,1740000000000,1746200000000,'application/pdf',NULL,NULL,1748600500000,NULL),
('od-2','Budget_2026.xlsx','/Dokumente/Finanzen/Budget_2026.xlsx','ONE_DRIVE','od',73422,1741000000000,1748300000000,'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',NULL,NULL,1748600500000,NULL),
('od-3','Rechnung_Handwerker.pdf','/Dokumente/Haus/Rechnung_Handwerker.pdf','ONE_DRIVE','od',129880,1742500000000,1747200000000,'application/pdf',NULL,NULL,1748600500000,NULL),
('od-4','Notizen_Meeting.txt','/Dokumente/Arbeit/Notizen_Meeting.txt','ONE_DRIVE','od',4096,1745800000000,1748450000000,'text/plain',NULL,NULL,1748600500000,NULL);

INSERT INTO file_content_fts (fileId,fileName,content) VALUES
('od-1','Mietvertrag_Wohnung.pdf','Mietvertrag über die Wohnung in der Musterstraße 12. Die monatliche Kaltmiete beträgt 980 EUR zuzüglich Nebenkosten. Das Mietverhältnis beginnt am 1. Juni 2026 und ist unbefristet.'),
('od-2','Budget_2026.xlsx','Budgetplanung 2026 nach Kategorien. Geplante Ausgaben für Miete, Versicherung, Lebensmittel und Rücklagen. Das Gesamtbudget pro Monat liegt bei 2.450 EUR. Die Rücklage wird monatlich erhöht.'),
('od-3','Rechnung_Handwerker.pdf','Rechnung des Handwerkerbetriebs für die Reparatur der Heizung. Arbeitszeit vier Stunden, Materialkosten 86 EUR. Rechnungsbetrag inklusive Anfahrt: 412,50 EUR. Vielen Dank für Ihren Auftrag.'),
('od-4','Notizen_Meeting.txt','Notizen zum Meeting am Montag. Themen: Projektstatus, offene Aufgaben und Termine. Nächstes Treffen in zwei Wochen. Protokoll wird per Mail verteilt.');

-- ============ DROPBOX (db) ============
INSERT INTO file_metadata (fileId,fileName,cloudPath,cloudProvider,accountId,fileSizeBytes,createdAt,modifiedAt,mimeType,changeToken,checksum,indexedAt,webUrl) VALUES
('db-1','Lebenslauf.pdf','/Bewerbung/Lebenslauf.pdf','DROPBOX','db',152044,1738000000000,1745000000000,'application/pdf',NULL,NULL,1748600500000,NULL),
('db-2','Reise_Italien.docx','/Reisen/Reise_Italien.docx','DROPBOX','db',38221,1739500000000,1746900000000,'application/vnd.openxmlformats-officedocument.wordprocessingml.document',NULL,NULL,1748600500000,NULL),
('db-3','Vertrag_Internet.pdf','/Vertraege/Vertrag_Internet.pdf','DROPBOX','db',98233,1737000000000,1744300000000,'application/pdf',NULL,NULL,1748600500000,NULL),
('db-4','Einkaufsliste.txt','/Notizen/Einkaufsliste.txt','DROPBOX','db',1280,1746500000000,1748500000000,'text/plain',NULL,NULL,1748600500000,NULL);

INSERT INTO file_content_fts (fileId,fileName,content) VALUES
('db-1','Lebenslauf.pdf','Lebenslauf mit Angaben zu Ausbildung und Berufserfahrung. Mehrjährige Erfahrung im Projektmanagement und in der Softwareentwicklung. Sprachkenntnisse: Deutsch, Englisch und Italienisch.'),
('db-2','Reise_Italien.docx','Reiseplanung für den Italien-Urlaub im Sommer. Route über Florenz, Rom und die Amalfiküste. Hotels sind gebucht, der Mietwagen ist reserviert. Budget für die Reise inklusive Verpflegung eingeplant.'),
('db-3','Vertrag_Internet.pdf','Vertrag über einen Internetanschluss mit 250 Mbit pro Sekunde. Die monatliche Grundgebühr beträgt 39,99 EUR. Die Mindestvertragslaufzeit beträgt 24 Monate. Kündigungsfrist drei Monate zum Vertragsende.'),
('db-4','Einkaufsliste.txt','Einkaufsliste für die Woche: Brot, Milch, Eier, Obst, Gemüse, Kaffee und Olivenöl. Außerdem Waschmittel und Zahnpasta nicht vergessen.');
