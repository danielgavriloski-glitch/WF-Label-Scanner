# MarK Booking 6.20 — translation audit (unverified / incomplete)

Source: branch `feature/mark-booking-v6-20-languages-documents`, Android project `td-booking-mark/admin`.

## Static interface text

- 141 unique Cyrillic text nodes in pre-script HTML (excluding CSS, editable contract terms).
- 66 matched by existing literal UI keys or prefix keys.
- **75 not covered by the current literal translation dictionary**.
- This is a **code-level audit**, not a device verification; counts are not a complete runtime coverage percentage.

### Uncovered UI strings

1. TD Production · верзија 6.20 · Резервации и екипа
2. Следен →
3. Невеста *
4. Младоженец *
5. Телефон на клиент *
6. Место / хотел
7. Резервирана
8. Завршена
9. Предадена
10. Откажана
11. Пакет *
12. Услуги договорени за оваа свадба *
13. Цена и подарок
14. Договорена цена пред попуст (€)
15. Попуст
16. Процент (%)
17. Вредност на попуст
18. Гратис вклучено во пакетот
19. Конечна цена (€)
20. Капар (€)
21. Рок за остаток
22. Назив на студио / изведувач *
23. Твоето целосно име за потпис *
24. Дополнителни договори и измени
25. Услови на договорот *
26. Прочитајте и дополнете ги условите за конкретниот настан пред потпишување.
27. Зачувај нацрт
28. Синхронизирај Google Calendar
29. Потписи на екранот
30. Потпишете со S Pen или прст. Секој потпис има свое поле.
31. TD Production / изведувач
32. Избриши потпис
33. Назад кон уредување
34. Избери свадба за да внесеш уплати од клиентите, ангажмани и исплати кон екипата.
35. Уплата од клиент
36. + Забележи уплата
37. Експрес · посебен приход плус
38. Внеси пари од продадени Експрес фотографии. Овој приход е плус, одделно од наплатата за пакетот и доплатите.
39. Примено (€)
40. Датум на продажба
41. Опис / број фотографии
42. + Додај продажба
43. Услугата се додава на договорениот износ. Ако уште не е платена, ќе се прикаже како долг.
44. Оваа услуга е гратис
45. Попуст на услугата
46. Вредност
47. + Додај услуга
48. Опис
49. + Додај трошок
50. Ангажмани за оваа свадба
51. + Додај фотограф / видео / кран / дрон / Love Story
52. Фотограф
53. Видеограф
54. Кран оператор
55. Дрон оператор
56. Love Story снимање
57. Монтажа / обработка
58. Задачи и опрема
59. Рок и начин на предавање материјал
60. Посебни услови
61. Ова е договор за конкретен ангажман. Ако лицето е во редовен работен однос, користете соодветен договор за вработување.
62. + Нова свадба
63. Соработници и нивните ангажмани низ сите свадби.
64. Зачувај PDF извештај
65. Целосна безбедносна копија
66. Ги зачувува свадбите, парите, екипата, потписите, пакетите и TD Cloud поврзувањето. Cloud најава се шифрира со лозинка што само ти ја знаеш.
67. 🔐 Направи ЦЕЛОСНА копија
68. Копирај текст
69. Обична копија (само локални податоци)
70. Свадби, финансии и пакетите.
71. Прикажи обична копија
72. Чувај ја копијата приватно. Целосната копија содржи шифриран Cloud пристап; без лозинката не може да се прочита.
73. Врати податоци
74. Провери и врати
75. Редовни цени за нови резервации. Договорените цени на постојните свадби остануваат исти.

### Placeholder / aria-label / title strings requiring separate handling

1. Барај име, телефон или датум
2. Напиши договорен рок
3. Име и презиме на потписникот
4. Пример: остаток
5. Пример: 35 слики за гости
6. Пример: дополнителен фотограф, албум, снимање
7. Гориво, пат, хотел…
8. Пример: оригинални датотеки следниот ден
9. Отвори мени
10. Главно мени

## Runtime and documents requiring separate audit

- Dynamic descriptions in `index.html`: client payments, crew receipts and payout data, financial reports, notices, alerts and network messages.
- Additional JS assets: `booking_home_v619.js`, `booking_finance_tabs_v619.js`, `booking_full_replica.js`, `td_mark_ui_v612.js`, `td_manual_crew.js`, `mark_brand.js`, `backup_export_v618.js`.
- User-entered text is intentionally not translated (names, notes, custom clauses, custom services). The app must disclose this clearly and must not claim a legally complete translation of signed documents.
- Multilingual PDF financial reports currently have simplified columns compared with the original Macedonian report; parity must be verified.
- All 4 languages need device tests covering create/edit booking; package configuration; financial balances; receipt and signed agreement; staff payments; backup/restore; sync; print/combined-language PDF and Unicode fonts.
- Never install a debug QA APK over an installed release. Validate the original keystore, package ID and upgrade signing before providing a production APK.

## Release gate

**Do not mark as 100% translated or production ready until every missing string is fixed, runtime translation tests pass, and a signed upgrade has been verified on device.**
