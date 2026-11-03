-- The four customer pages linked from the site footer ("Xaridorlarga"): faq, delivery, returns, public-offer.
-- First versions only: ADMIN edits them in the admin panel (Content -> Pages); an existing page is never overwritten.
-- The public offer is a template with [TO'LDIRING] placeholders, so it is NOT published: a lawyer fills in the
-- company details, then ADMIN publishes it. Prices and days are not written as numbers (they are settings).
INSERT INTO pages (id, slug, title_uz, title_ru, body_uz, body_ru, published, created_at, updated_at, version)
VALUES
-- ---------------------------------------------------------------------------------------------------------- faq
(gen_random_uuid(), 'faq', 'Ko''p beriladigan savollar', 'Частые вопросы',
$uz$
## E-Market nima?

E-Market — Xitoyning Taobao platformasidagi mahsulotlarni O'zbekistonga olib kelib beradigan vositachi xizmat.
Siz mahsulotni saytimizda tanlaysiz va so'mda to'laysiz. Xitoydagi xaridorimiz uni Taobao'dan sotib oladi,
hamkor kargo kompaniyasi esa O'zbekistonga yetkazib beradi.

## Qanday buyurtma beraman?

1. Mahsulotni qidiruvda (o'zbek yoki rus tilida) toping yoki Taobao havolasini qidiruv maydoniga joylang.
2. Rang, o'lcham va sonini tanlab, savatga qo'shing.
3. Savatda "Rasmiylashtirish" tugmasini bosing va to'lovni amalga oshiring.

Kirish uchun telefon raqamingizni kiriting — tasdiqlash kodi Telegram bot orqali keladi.

## Narx qanday hisoblanadi?

Saytda ko'rsatilgan yakuniy narx so'mda va quyidagilardan iborat:

- **tovar narxi** — Taobao'dagi narx va Xitoy ichidagi yetkazish, kunlik kurs bo'yicha so'mga o'girilgan;
- **xizmat haqi** — tovar narxining foizi (kamida belgilangan minimal summa);
- **O'zbekistongacha yetkazish** — tovar og'irligi bo'yicha, 1 kg uchun tarif asosida.

Barcha summalar savatda va to'lovdan oldin alohida ko'rsatiladi. Yashirin to'lovlar yo'q.

## Qanday to'lash mumkin?

Payme, Click yoki E-Market hamyoningizdagi mablag' bilan. To'lanmagan buyurtma 30 daqiqadan so'ng
avtomatik bekor qilinadi.

## Tovar qancha vaqtda keladi?

Taxminiy muddat mahsulot sahifasida va bosh sahifada ko'rsatiladi. Muddat to'lovdan keyin hisoblanadi va
sotuvchi tovarni qancha tez jo'natishiga bog'liq.

## Buyurtmam qayerdaligini qanday bilaman?

Kabinet → **Buyurtmalarim** bo'limida har bir buyurtmaning holati ko'rinadi: to'landi, sotib olinmoqda,
sotib olindi, Xitoy omborida, yo'lda, O'zbekistonga yetib keldi, topshirildi. Har bir o'zgarish haqida
Telegram orqali xabar yuboramiz.

## Nega yetkazish uchun qo'shimcha to'lov so'ralyapti?

Yetkazish narxi avval taxminiy og'irlik bo'yicha olinadi. Tovar Xitoy omborida tortilgach:

- haqiqiy og'irlik **ko'proq** bo'lsa, farqni to'lash so'raladi (buyurtma "Yetkazish farqi to'lanishi kerak"
  holatida, Kabinetda "Yetkazish farqini to'lash" tugmasi);
- **kamroq** bo'lsa, farq hamyoningizga qaytariladi.

## Tovarning narxi o'zgarsa yoki u tugab qolsa-chi?

Xarid to'xtatiladi, Telegram orqali xabar beramiz va Kabinetdagi buyurtma sahifasida o'zingiz qaror qilasiz:

- **Narx o'zgargan bo'lsa** — "Davom etish" (narx farqini to'laysiz) yoki "Bekor qilish va pulni qaytarish".
- **Ba'zi mahsulotlar tugagan bo'lsa** — "Davom etish" (tugaganlarining puli hamyonga qaytadi, qolganlari
  sotib olinadi) yoki "Bekor qilish va pulni qaytarish".
- **Buyurtmadagi hamma mahsulot tugagan bo'lsa**, buyurtma bekor qilinadi va to'langan summa to'liq
  hamyoningizga qaytariladi.

## Buyurtmani bekor qilsam bo'ladimi?

Ha, xaridorimiz tovarni sotib olishga kirishguncha — buyurtma "To'lov kutilmoqda" yoki "To'landi"
holatida bo'lganda. Kabinetdagi buyurtma sahifasida "Buyurtmani bekor qilish" tugmasini bosing; to'langan
summa hamyoningizga qaytadi. Tovar sotib olingandan keyin buyurtmani bekor qilib bo'lmaydi.

## Hamyon nima va undagi pulni qaytarib olsa bo'ladimi?

Hamyon — E-Market'dagi shaxsiy balansingiz. Unga qaytarilgan summalar tushadi va keyingi buyurtmalarni
shu mablag' bilan to'lash mumkin. Pulni kartaga qaytarishni xohlasangiz, qo'llab-quvvatlash xizmatimizga
murojaat qiling — operator uni kartangizga qaytaradi.

## Har qanday tovarni buyurtma qilsa bo'ladimi?

Yo'q. Suyuqliklar (atir va boshqalar), dori-darmon, qurol, katta sig'imli akkumulyatorlar va brend
nusxalari (replikalar) qabul qilinmaydi — bunday tovarlar qidiruvda ko'rsatilmaydi.
$uz$,
$ru$
## Что такое E-Market?

E-Market — сервис-посредник, который привозит в Узбекистан товары с китайской площадки Taobao.
Вы выбираете товар на нашем сайте и платите в сумах. Наш закупщик в Китае покупает его на Taobao,
а партнёрская карго-компания доставляет в Узбекистан.

## Как сделать заказ?

1. Найдите товар через поиск (на узбекском или русском) или вставьте ссылку Taobao в строку поиска.
2. Выберите цвет, размер и количество и добавьте товар в корзину.
3. В корзине нажмите «Оформить» и оплатите заказ.

Для входа укажите номер телефона — код подтверждения придёт через Telegram-бот.

## Как считается цена?

Итоговая цена на сайте указана в сумах и состоит из:

- **стоимости товара** — цена на Taobao и доставка по Китаю, по дневному курсу в сумах;
- **сервисного сбора** — процент от стоимости товара (не меньше установленной минимальной суммы);
- **доставки до Узбекистана** — по весу товара, по тарифу за 1 кг.

Все суммы отдельно показаны в корзине и перед оплатой. Скрытых платежей нет.

## Как можно оплатить?

Через Payme, Click или с баланса кошелька E-Market. Неоплаченный заказ автоматически отменяется
через 30 минут.

## Когда придёт товар?

Ориентировочный срок указан на странице товара и на главной странице. Срок считается с момента оплаты
и зависит от того, как быстро продавец отправит товар.

## Как узнать, где мой заказ?

В Кабинете → **Мои заказы** виден статус каждого заказа: оплачен, выкупается, выкуплен, на складе в Китае,
в пути, прибыл в Узбекистан, получен. О каждом изменении мы сообщаем в Telegram.

## Почему просят доплатить за доставку?

Сначала доставка оплачивается по примерному весу. Когда товар взвешивают на складе в Китае:

- если фактический вес **больше**, нужно доплатить разницу (заказ в статусе «Нужно доплатить за доставку»,
  кнопка в Кабинете);
- если **меньше**, разница возвращается в ваш кошелёк.

## Что если цена товара изменится или он закончится?

Выкуп приостанавливается, мы сообщаем в Telegram, и вы сами решаете на странице заказа в Кабинете:

- **Если цена изменилась** — «Продолжить» (вы доплачиваете разницу в цене) или «Отменить и вернуть деньги».
- **Если закончилась часть товаров** — «Продолжить» (деньги за закончившиеся вернутся в кошелёк, остальные
  будут выкуплены) или «Отменить и вернуть деньги».
- **Если закончились все товары заказа**, заказ отменяется, а оплаченная сумма полностью возвращается в кошелёк.

## Можно ли отменить заказ?

Да, пока закупщик не начал выкуп — когда заказ в статусе «Ожидает оплаты» или «Оплачен». Нажмите
«Отменить заказ» на странице заказа в Кабинете; оплаченная сумма вернётся в кошелёк. После выкупа товара
заказ отменить нельзя.

## Что такое кошелёк и можно ли вывести с него деньги?

Кошелёк — ваш личный баланс в E-Market. На него поступают возвраты, и им можно оплачивать следующие
заказы. Если хотите вернуть деньги на карту, обратитесь в службу поддержки — оператор вернёт их на вашу карту.

## Можно ли заказать любой товар?

Нет. Жидкости (духи и т. п.), лекарства, оружие, аккумуляторы большой ёмкости и копии брендов (реплики)
не принимаются — такие товары не показываются в поиске.
$ru$,
 TRUE, now(), now(), 0),

-- ----------------------------------------------------------------------------------------------------- delivery
(gen_random_uuid(), 'delivery', 'Yetkazib berish', 'Доставка',
$uz$
## Tovar qanday yo'l bosib o'tadi

1. **To'lov.** Buyurtmani to'laganingizdan so'ng u xaridorimizga o'tadi.
2. **Xarid.** Xitoydagi xaridorimiz tovarni Taobao sotuvchisidan sotib oladi.
3. **Xitoydagi ombor.** Sotuvchi tovarni Xitoy ichidagi omborga jo'natadi. U yerda tovar tortiladi va
   haqiqiy og'irligi aniqlanadi.
4. **Yo'lda.** Hamkor kargo kompaniyasi yoki Taobao'ning rasmiy jamlash xizmati tovarni O'zbekistonga olib keladi.
5. **Yetib keldi.** Tovar O'zbekistonga kelganda Telegram orqali xabar beramiz.
6. **Topshirish.** Tovarni o'zingizga qulay usulda olasiz (pastda).

Har bir bosqich Kabinet → **Buyurtmalarim** bo'limida ko'rinadi.

## Muddat

Taxminiy yetkazish muddati mahsulot sahifasida ko'rsatiladi va to'lov kunidan hisoblanadi. Muddat sotuvchi
tovarni qancha tez jo'natishiga, bayram kunlariga va bojxona tekshiruviga bog'liq bo'lishi mumkin.

## Narx

Yetkazish narxi tovar og'irligi bo'yicha, 1 kg uchun tarif asosida hisoblanadi (minimal hisoblanadigan
og'irlik bor). U buyurtma summasiga kiradi va savatda alohida ko'rsatiladi.

Avval taxminiy og'irlik bo'yicha to'lanadi. Tovar Xitoy omborida tortilgach:

- og'irlik **ko'proq** chiqsa — farqni to'lash so'raladi, tovar farq to'langandan keyin jo'natiladi;
- og'irlik **kamroq** chiqsa — farq hamyoningizga qaytariladi.

## Tovarni qanday olasiz

Olish usulini buyurtmani rasmiylashtirishda tanlaysiz:

- **Kargo ofisidan olib ketish** — hamkor kargo kompaniyasining ofisidan o'zingiz olasiz, manzil kerak emas.
  Ofis manzili va ish vaqtini tovar yetib kelganda xabarnomada yuboramiz.
- **Kuryer** — tovarni eshigingizgacha olib borishadi.
- **O'zbekiston pochtasi** — tovar ko'rsatgan manzilingizga pochta orqali yuboriladi.

Kuryer va pochta orqali O'zbekiston ichida yetkazish narxi va muddati shahar yoki viloyatga bog'liq —
operatorimiz oldindan xabar beradi. Manzillarni Kabinet → **Manzillar** bo'limida saqlab qo'yishingiz mumkin.

## Tovarni olayotganda

Qadoqni topshirish vaqtida ko'zdan kechiring. Qadoq shikastlangan bo'lsa, buni darhol kargo xodimiga
ayting va suratga oling.
$uz$,
$ru$
## Путь товара

1. **Оплата.** После оплаты заказ передаётся закупщику.
2. **Выкуп.** Наш закупщик в Китае покупает товар у продавца на Taobao.
3. **Склад в Китае.** Продавец отправляет товар на склад внутри Китая. Там товар взвешивают и узнают
   фактический вес.
4. **В пути.** Партнёрская карго-компания или официальная консолидация Taobao привозит товар в Узбекистан.
5. **Прибыл.** Когда товар прибудет в Узбекистан, мы сообщим в Telegram.
6. **Получение.** Вы получаете товар удобным способом (ниже).

Каждый этап виден в Кабинете → **Мои заказы**.

## Срок

Ориентировочный срок доставки указан на странице товара и считается со дня оплаты. Он может зависеть от того,
как быстро продавец отправит товар, от праздничных дней и таможенной проверки.

## Стоимость

Доставка рассчитывается по весу товара, по тарифу за 1 кг (есть минимальный учитываемый вес). Она входит
в сумму заказа и отдельно показана в корзине.

Сначала оплачивается примерный вес. Когда товар взвешивают на складе в Китае:

- если вес **больше** — нужно доплатить разницу, товар отправится после доплаты;
- если вес **меньше** — разница возвращается в ваш кошелёк.

## Как получить товар

Способ получения выбирается при оформлении заказа:

- **Самовывоз из офиса карго** — вы забираете товар в офисе партнёрской карго-компании, адрес не нужен.
  Адрес и часы работы офиса пришлём в уведомлении, когда товар прибудет.
- **Курьер** — товар привезут до двери.
- **Почта Узбекистана** — товар отправят почтой по указанному адресу.

Стоимость и срок доставки курьером и почтой внутри Узбекистана зависят от города или области — оператор
сообщит заранее. Адреса можно сохранить в Кабинете → **Адреса**.

## При получении

Осмотрите упаковку при получении. Если она повреждена, сразу сообщите сотруднику карго и сфотографируйте её.
$ru$,
 TRUE, now(), now(), 0),

-- ------------------------------------------------------------------------------------------------------ returns
(gen_random_uuid(), 'returns', 'Qaytarish va pulni qaytarish', 'Возврат товара и денег',
$uz$
## Tovarni qaytarish

E-Market tovarlarni Xitoydagi sotuvchilardan sizning buyurtmangiz bo'yicha sotib oladi. Shu sababli
**olingan tovar qaytarib olinmaydi va almashtirilmaydi**. Buyurtma berishdan oldin mahsulot tavsifini,
rangini va o'lchamlar jadvalini diqqat bilan tekshiring.

## Pul qachon qaytariladi

Pul quyidagi hollarda qaytariladi:

- **Buyurtmani bekor qilsangiz** — xaridorimiz tovarni sotib olishga kirishguncha ("To'lov kutilmoqda" yoki
  "To'landi" holatida). Kabinetdagi buyurtma sahifasida "Buyurtmani bekor qilish" tugmasini bosing.
- **Mahsulot sotuvchida tugagan bo'lsa** — tugagan mahsulotlar puli qaytariladi ("Davom etish"), yoki butun
  buyurtmani bekor qilib, to'liq pulni qaytarishingiz mumkin ("Bekor qilish va pulni qaytarish"). Hamma
  mahsulot tugagan bo'lsa, to'langan summa to'liq qaytariladi.
- **Tovar narxi o'zgargan bo'lsa** va siz yangi narxga rozi bo'lmasangiz — Kabinetda "Bekor qilish va pulni
  qaytarish"ni tanlang.
- **Haqiqiy og'irlik taxminiydan kam chiqsa** — yetkazish narxidagi farq qaytariladi.
- **Buyurtmani biz bekor qilsak** — to'langan summa to'liq qaytariladi.

Tovar sotib olingandan keyin buyurtmani bekor qilib bo'lmaydi.

## Pul qayerga qaytadi

Qaytarilgan summa darhol **E-Market hamyoningizga** tushadi va Telegram orqali xabar beramiz.
Hamyondagi mablag' bilan keyingi buyurtmalarni to'lashingiz mumkin.

Pulni bank kartangizga qaytarishni xohlasangiz, qo'llab-quvvatlash xizmatimizga murojaat qiling — operator
summani to'lov qilingan kartaga qaytaradi. Kartaga tushish muddati bankka bog'liq.
$uz$,
$ru$
## Возврат товара

E-Market выкупает товары у продавцов в Китае под ваш заказ. Поэтому **полученный товар не принимается
обратно и не обменивается**. Перед заказом внимательно проверьте описание, цвет и таблицу размеров.

## Когда возвращаются деньги

Деньги возвращаются, если:

- **Вы отменили заказ**, пока закупщик не начал выкуп (статус «Ожидает оплаты» или «Оплачен»). Нажмите
  «Отменить заказ» на странице заказа в Кабинете.
- **Товар закончился у продавца** — возвращаются деньги за закончившиеся товары («Продолжить»), или можно
  отменить весь заказ и вернуть всю сумму («Отменить и вернуть деньги»). Если закончились все товары,
  оплаченная сумма возвращается полностью.
- **Цена товара изменилась**, и вы не согласны с новой ценой — выберите в Кабинете «Отменить и вернуть деньги».
- **Фактический вес меньше примерного** — возвращается разница в стоимости доставки.
- **Заказ отменили мы** — оплаченная сумма возвращается полностью.

После выкупа товара заказ отменить нельзя.

## Куда возвращаются деньги

Возвращённая сумма сразу поступает в ваш **кошелёк E-Market**, и мы сообщаем об этом в Telegram.
Средствами кошелька можно оплачивать следующие заказы.

Если хотите вернуть деньги на банковскую карту, обратитесь в службу поддержки — оператор вернёт сумму на карту,
с которой была оплата. Срок зачисления на карту зависит от банка.
$ru$,
 TRUE, now(), now(), 0),

-- ------------------------------------------------------------------------------------------------- public offer
(gen_random_uuid(), 'public-offer', 'Ommaviy oferta', 'Публичная оферта',
$uz$
> **Shablon.** Kvadrat qavs ichidagi [TO'LDIRING] joylarini to'ldiring va matnni yuristga tekshirtiring.
> Keyin bu blokni o'chiring va sahifani e'lon qiling: Admin → Kontent → `public-offer` sahifasi → "E'lon qilingan".

## 1. Umumiy qoidalar

1.1. Ushbu hujjat [TO'LDIRING: kompaniya nomi] (keyingi o'rinlarda — **Ijrochi**) tomonidan E-Market sayti
([TO'LDIRING: sayt manzili]) orqali xizmatlardan foydalanuvchi har qanday jismoniy shaxsga (keyingi o'rinlarda —
**Mijoz**) qaratilgan ommaviy ofertadir (O'zbekiston Respublikasi Fuqarolik kodeksining 369-moddasi).

1.2. Mijoz buyurtmani to'lagan paytdan boshlab ushbu oferta shartlarini to'liq qabul qilgan (aksept) hisoblanadi
va Ijrochi bilan shartnoma tuzilgan bo'ladi.

1.3. Ijrochi oferta shartlarini o'zgartirishi mumkin. Yangi tahrir saytda e'lon qilingan kundan kuchga kiradi va
undan keyin to'langan buyurtmalarga qo'llaniladi.

## 2. Shartnoma predmeti

2.1. Ijrochi vositachi sifatida Mijozning topshirig'i bo'yicha Xitoy Xalq Respublikasidagi Taobao platformasida
Mijoz tanlagan tovarni sotib oladi va uni O'zbekiston Respublikasiga yetkazib berishni tashkil qiladi.

2.2. Ijrochi tovarning ishlab chiqaruvchisi yoki sotuvchisi emas. Tovarning tavsifi, sifati va xususiyatlari
uchun Taobao'dagi sotuvchi javobgar.

2.3. Tovarni O'zbekistonga tashish hamkor kargo kompaniyasi yoki Taobao'ning rasmiy jamlash xizmati
orqali amalga oshiriladi.

## 3. Buyurtma va narx

3.1. Mijoz saytda tovarni, uning variantini (rang, o'lcham) va sonini tanlab buyurtma beradi.

3.2. Buyurtma narxi so'mda ko'rsatiladi va quyidagilardan iborat: tovar narxi (Taobao'dagi narx va Xitoy
ichidagi yetkazish, Ijrochi belgilagan kurs bo'yicha), Ijrochining xizmat haqi va O'zbekistongacha yetkazish narxi.
Narx tarkibi to'lovdan oldin Mijozga ko'rsatiladi.

3.3. Yetkazish narxi avval taxminiy og'irlik bo'yicha hisoblanadi. Tovar tortilgach, haqiqiy narx ko'proq bo'lsa,
Mijoz farqni to'laydi; kamroq bo'lsa, farq Mijozning hamyoniga qaytariladi.

3.4. Taobao'dagi narx xarid paytida o'zgargan bo'lsa, Ijrochi xaridni to'xtatadi va Mijoz yangi narxga rozi bo'lish
yoki pulni qaytarishni tanlaydi.

## 4. To'lov

4.1. To'lov Payme, Click to'lov tizimlari yoki Mijozning saytdagi hamyoni orqali 100% oldindan amalga oshiriladi.

4.2. 30 daqiqa ichida to'lanmagan buyurtma avtomatik bekor qilinadi.

## 5. Yetkazib berish va topshirish

5.1. Taxminiy yetkazish muddati saytda ko'rsatiladi va to'lov kunidan hisoblanadi. Muddat sotuvchi, transport
va bojxona sabablari bilan o'zgarishi mumkin; bunday kechikish shartnomaning buzilishi hisoblanmaydi.

5.2. Mijoz buyurtmani rasmiylashtirishda olish usulini tanlaydi: hamkor kargo kompaniyasining ofisidan olib
ketish, kuryer yoki O'zbekiston pochtasi. Kuryer va pochta orqali O'zbekiston ichida yetkazish narxi alohida
kelishiladi.

5.3. Mijoz tovarni olayotganda qadoqni ko'zdan kechirishi shart. Qadoq shikastlanganligi haqidagi da'vo
faqat topshirish paytida qabul qilinadi.

## 6. Bekor qilish va pulni qaytarish

6.1. Mijoz buyurtmani Ijrochi xaridni boshlaguncha bekor qilishi mumkin; to'langan summa hamyonga qaytariladi.

6.2. Tovarning bir qismi sotuvchida tugagan bo'lsa, Mijoz tugagan tovarlar puli qaytarilgan holda xaridni davom
ettirish yoki buyurtmani bekor qilishni tanlaydi. Hamma tovar tugagan yoki Ijrochi buyurtmani bekor qilgan bo'lsa,
to'langan summa to'liq qaytariladi.

6.3. Tovar Mijozning buyurtmasi bo'yicha xorijdan sotib olinganligi sababli, olingan tovar qaytarib olinmaydi va
almashtirilmaydi.

6.4. Qaytarilgan summa Mijozning saytdagi hamyoniga tushadi. Mijozning murojaatiga ko'ra summa to'lov qilingan
kartaga qaytariladi.

## 7. Cheklangan tovarlar

7.1. Ijrochi O'zbekiston Respublikasiga olib kirish taqiqlangan yoki cheklangan tovarlarni (suyuqliklar, dori
vositalari, qurollar, katta sig'imli akkumulyatorlar, brend nusxalari va boshqalar) sotib olmaydi. Bunday tovar
buyurtma qilingan bo'lsa, buyurtma bekor qilinadi va pul qaytariladi.

## 8. Taraflarning javobgarligi

8.1. Ijrochi xizmatni o'z vaqtida va sifatli ko'rsatish uchun javobgar. Ijrochining javobgarligi Mijoz to'lagan
buyurtma summasi bilan cheklanadi.

8.2. Ijrochi tovar sifati, sotuvchi tomonidan berilgan noto'g'ri ma'lumot, shuningdek fors-major holatlari
(tabiiy ofatlar, davlat organlarining qarorlari, transport to'xtashi va boshqalar) uchun javobgar emas.

8.3. Mijoz buyurtmada to'g'ri ma'lumot (telefon, manzil) ko'rsatishi uchun javobgar.

## 9. Shaxsiy ma'lumotlar

9.1. Mijoz buyurtmani bajarish uchun telefon raqami, ismi va manzili qayta ishlanishiga rozilik beradi.
Ma'lumotlar "Shaxsga doir ma'lumotlar to'g'risida"gi O'zbekiston Respublikasi Qonuniga muvofiq himoya qilinadi
va faqat buyurtmani bajarish uchun (shu jumladan kargo kompaniyasiga) beriladi.

## 10. Nizolarni hal qilish

10.1. Nizolar muzokaralar yo'li bilan hal qilinadi. Kelishuvga erishilmasa, nizo O'zbekiston Respublikasi
qonunchiligiga muvofiq sudda ko'rib chiqiladi.

## 11. Ijrochining rekvizitlari

- Nomi: [TO'LDIRING]
- STIR: [TO'LDIRING]
- Yuridik manzil: [TO'LDIRING]
- Bank, hisob raqami, MFO: [TO'LDIRING]
- Telefon: [TO'LDIRING]
- Elektron pochta: [TO'LDIRING]
$uz$,
$ru$
> **Шаблон.** Заполните места [ЗАПОЛНИТЬ] в квадратных скобках и отдайте текст на проверку юристу.
> Затем удалите этот блок и опубликуйте страницу: Админ → Контент → страница `public-offer` → «Опубликована».

## 1. Общие положения

1.1. Настоящий документ является публичной офертой [ЗАПОЛНИТЬ: название компании] (далее — **Исполнитель**),
адресованной любому физическому лицу, пользующемуся услугами через сайт E-Market ([ЗАПОЛНИТЬ: адрес сайта])
(далее — **Клиент**) (статья 369 Гражданского кодекса Республики Узбекистан).

1.2. С момента оплаты заказа Клиент считается полностью принявшим (акцептовавшим) условия оферты, и договор
с Исполнителем считается заключённым.

1.3. Исполнитель вправе изменять условия оферты. Новая редакция вступает в силу со дня публикации на сайте и
применяется к заказам, оплаченным после этого.

## 2. Предмет договора

2.1. Исполнитель как посредник по поручению Клиента выкупает выбранный Клиентом товар на площадке Taobao
в Китайской Народной Республике и организует его доставку в Республику Узбекистан.

2.2. Исполнитель не является производителем или продавцом товара. За описание, качество и характеристики
товара отвечает продавец на Taobao.

2.3. Перевозка товара в Узбекистан осуществляется партнёрской карго-компанией или официальной
консолидацией Taobao.

## 3. Заказ и цена

3.1. Клиент оформляет заказ на сайте, выбрав товар, его вариант (цвет, размер) и количество.

3.2. Цена заказа указывается в сумах и состоит из: стоимости товара (цена на Taobao и доставка по Китаю,
по курсу Исполнителя), сервисного сбора Исполнителя и стоимости доставки до Узбекистана. Состав цены
показывается Клиенту до оплаты.

3.3. Доставка сначала рассчитывается по примерному весу. После взвешивания, если фактическая стоимость больше,
Клиент доплачивает разницу; если меньше, разница возвращается в кошелёк Клиента.

3.4. Если цена на Taobao изменилась при выкупе, Исполнитель приостанавливает выкуп, и Клиент выбирает:
согласиться с новой ценой или вернуть деньги.

## 4. Оплата

4.1. Оплата производится на 100% заранее через платёжные системы Payme, Click или кошелёк Клиента на сайте.

4.2. Заказ, не оплаченный в течение 30 минут, отменяется автоматически.

## 5. Доставка и вручение

5.1. Ориентировочный срок доставки указан на сайте и считается со дня оплаты. Срок может измениться по причинам,
зависящим от продавца, перевозки и таможни; такая задержка не является нарушением договора.

5.2. Клиент выбирает способ получения при оформлении заказа: самовывоз из офиса партнёрской карго-компании,
курьер или Почта Узбекистана. Стоимость доставки курьером и почтой внутри Узбекистана согласуется отдельно.

5.3. При получении Клиент обязан осмотреть упаковку. Претензия о повреждении упаковки принимается только
в момент вручения.

## 6. Отмена и возврат денег

6.1. Клиент может отменить заказ, пока Исполнитель не начал выкуп; оплаченная сумма возвращается в кошелёк.

6.2. Если часть товаров закончилась у продавца, Клиент выбирает: продолжить выкуп с возвратом денег за закончившиеся
товары или отменить заказ. Если закончились все товары или заказ отменил Исполнитель, оплаченная сумма
возвращается полностью.

6.3. Так как товар выкупается за рубежом под заказ Клиента, полученный товар не принимается обратно и
не обменивается.

6.4. Возвращённая сумма поступает в кошелёк Клиента на сайте. По обращению Клиента сумма возвращается на карту,
с которой была оплата.

## 7. Ограниченные товары

7.1. Исполнитель не выкупает товары, ввоз которых в Республику Узбекистан запрещён или ограничен (жидкости,
лекарства, оружие, аккумуляторы большой ёмкости, копии брендов и т. п.). Если такой товар заказан, заказ
отменяется, а деньги возвращаются.

## 8. Ответственность сторон

8.1. Исполнитель отвечает за своевременное и качественное оказание услуги. Ответственность Исполнителя
ограничена суммой заказа, оплаченной Клиентом.

8.2. Исполнитель не отвечает за качество товара, неверные сведения продавца, а также за форс-мажор
(стихийные бедствия, решения государственных органов, остановка перевозок и т. п.).

8.3. Клиент отвечает за правильность данных в заказе (телефон, адрес).

## 9. Персональные данные

9.1. Клиент даёт согласие на обработку номера телефона, имени и адреса для выполнения заказа. Данные защищаются
в соответствии с Законом Республики Узбекистан «О персональных данных» и передаются только для выполнения
заказа (в том числе карго-компании).

## 10. Разрешение споров

10.1. Споры решаются путём переговоров. Если соглашение не достигнуто, спор рассматривается в суде в соответствии
с законодательством Республики Узбекистан.

## 11. Реквизиты Исполнителя

- Название: [ЗАПОЛНИТЬ]
- ИНН: [ЗАПОЛНИТЬ]
- Юридический адрес: [ЗАПОЛНИТЬ]
- Банк, расчётный счёт, МФО: [ЗАПОЛНИТЬ]
- Телефон: [ЗАПОЛНИТЬ]
- Электронная почта: [ЗАПОЛНИТЬ]
$ru$,
 FALSE, now(), now(), 0)
ON CONFLICT (slug) DO NOTHING;
