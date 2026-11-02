"use client";

import Image from "next/image";
import { useTranslations } from "next-intl";
import { useEffect, useState } from "react";
import type { Banner } from "@/api/types";
import { Link } from "@/i18n/navigation";

const heroClass =
  "relative flex min-h-[260px] flex-col justify-center gap-4 overflow-hidden rounded-lg bg-navy px-[22px] py-7 text-white min-[561px]:px-9 min-[561px]:py-10";
const titleClass =
  "m-0 text-[clamp(28px,3.4vw,42px)] leading-[1.08] font-extrabold tracking-[-0.02em] [text-wrap:balance]";

/** Navy hero (design: .hero). Shows /banners when there are any, otherwise the default hero text. */
export function HeroBanners({ banners, popularHref }: { banners: Banner[]; popularHref: string }) {
  const t = useTranslations("home");
  const [index, setIndex] = useState(0);
  const count = banners.length;

  useEffect(() => {
    if (count < 2 || window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;
    const id = setInterval(() => setIndex((i) => (i + 1) % count), 6000);
    return () => clearInterval(id);
  }, [count]);

  if (count === 0) {
    return (
      <div className={heroClass}>
        <span className="text-[13px] font-bold tracking-[0.14em] uppercase">{t("eyebrow")}</span>
        <h1 className={titleClass}>{t.rich("heroTitle", { br: () => <br /> })}</h1>
        <a href={popularHref} className="btn btn-orange self-start">
          {t("heroCta")}
        </a>
      </div>
    );
  }

  const banner = banners[Math.min(index, count - 1)];
  const link = banner.linkUrl ?? "";
  const external = /^https?:\/\//.test(link);

  return (
    <section className={heroClass} aria-roledescription="carousel" aria-label={t("bannersLabel")}>
      {banner.imageUrl && (
        <Image
          src={banner.imageUrl}
          alt=""
          fill
          priority
          sizes="(max-width: 900px) 100vw, 760px"
          className="object-cover opacity-35"
        />
      )}
      <div className="relative flex flex-col gap-4" aria-live="polite">
        <span className="text-[13px] font-bold tracking-[0.14em] uppercase">{t("eyebrow")}</span>
        <h1 className={titleClass}>{banner.title}</h1>
        {banner.subtitle && <p className="m-0 max-w-[46ch] text-[15px] opacity-90">{banner.subtitle}</p>}
        {link &&
          (external ? (
            <a href={link} className="btn btn-orange self-start" rel="noopener">
              {t("bannerCta")}
            </a>
          ) : (
            <Link href={link} className="btn btn-orange self-start">
              {t("bannerCta")}
            </Link>
          ))}
      </div>
      {count > 1 && (
        <div className="relative flex gap-1.5">
          {banners.map((b, i) => (
            <button
              key={b.id ?? i}
              type="button"
              onClick={() => setIndex(i)}
              aria-label={t("bannerDot", { index: i + 1 })}
              aria-current={i === index ? "true" : undefined}
              className={`h-2 rounded-full border-0 p-0 ${i === index ? "w-5 bg-orange" : "w-2 bg-white/50"}`}
            />
          ))}
        </div>
      )}
    </section>
  );
}
