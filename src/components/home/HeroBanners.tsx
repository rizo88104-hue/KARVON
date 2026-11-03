"use client";

import Image from "next/image";
import { useTranslations } from "next-intl";
import { useEffect, useRef, useState } from "react";
import type { Banner } from "@/api/types";
import { Link } from "@/i18n/navigation";

/** How long one slide is shown; .hero-dot-fill in globals.css fills the active dot in the same time. */
const SLIDE_MS = 6000;

const heroClass =
  "relative flex min-h-[260px] flex-col justify-center gap-4 overflow-hidden rounded-lg bg-navy px-[22px] py-7 text-white min-[561px]:px-9 min-[561px]:py-10";
const titleClass =
  "m-0 text-[clamp(28px,3.4vw,42px)] leading-[1.08] font-extrabold tracking-[-0.02em] [text-wrap:balance]";

/**
 * Navy hero (design: .hero). Shows /banners when there are any, otherwise the default hero text.
 * Slides move sideways on one track (also by swiping on touch screens and with the arrow keys), the active photo zooms
 * in slowly and its text rises in (styles in globals.css, all off with prefers-reduced-motion). Slides change every SLIDE_MS while the active dot fills. The carousel pauses while the
 * pointer or keyboard focus is on it and then goes on with the time that was left, in step with the dot.
 * With reduced motion it does not change slides by itself.
 */
export function HeroBanners({ banners, popularHref }: { banners: Banner[]; popularHref: string }) {
  const t = useTranslations("home");
  const [index, setIndex] = useState(0);
  const [paused, setPaused] = useState(false);
  const count = banners.length;
  const active = Math.min(index, Math.max(count - 1, 0));
  const remaining = useRef(SLIDE_MS);
  const swipeFrom = useRef<number | null>(null);

  // A new slide gets the full time (this effect runs before the timer effect below).
  useEffect(() => {
    remaining.current = SLIDE_MS;
  }, [active]);

  useEffect(() => {
    if (count < 2 || paused || window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;
    const started = performance.now();
    const id = setTimeout(() => setIndex((i) => (i + 1) % count), remaining.current);
    return () => {
      clearTimeout(id);
      remaining.current = Math.max(0, remaining.current - (performance.now() - started));
    };
  }, [active, count, paused]);

  if (count === 0) {
    return (
      <div className={heroClass}>
        <div className="hero-in flex flex-col gap-4">
          <span className="text-[13px] font-bold tracking-[0.14em] uppercase">{t("eyebrow")}</span>
          <h1 className={titleClass}>{t.rich("heroTitle", { br: () => <br /> })}</h1>
          <a href={popularHref} className="btn btn-orange self-start">
            {t("heroCta")}
          </a>
        </div>
      </div>
    );
  }

  const go = (step: number) => setIndex((i) => (i + step + count) % count);

  return (
    <section
      className="relative overflow-hidden rounded-lg bg-navy text-white touch-pan-y"
      aria-roledescription="carousel"
      aria-label={t("bannersLabel")}
      data-paused={paused || undefined}
      onMouseEnter={() => setPaused(true)}
      onMouseLeave={() => setPaused(false)}
      onFocus={() => setPaused(true)}
      onBlur={(e) => {
        if (!e.currentTarget.contains(e.relatedTarget as Node | null)) setPaused(false);
      }}
      onKeyDown={(e) => {
        if (e.key === "ArrowLeft") go(-1);
        if (e.key === "ArrowRight") go(1);
      }}
      // Swipe on touch screens: a horizontal move of 50 px or more changes the slide.
      onPointerDown={(e) => {
        if (e.pointerType !== "mouse") swipeFrom.current = e.clientX;
      }}
      onPointerUp={(e) => {
        const from = swipeFrom.current;
        swipeFrom.current = null;
        if (from === null || Math.abs(e.clientX - from) < 50) return;
        go(e.clientX < from ? 1 : -1);
      }}
      onPointerCancel={() => {
        swipeFrom.current = null;
      }}
    >
      {/* All slides sit side by side on one track, which slides to the active one (.hero-track). */}
      <div className="hero-track flex" style={{ transform: `translateX(-${active * 100}%)` }}>
        {banners.map((b, i) => {
          const current = i === active;
          const link = b.linkUrl ?? "";
          const external = /^https?:///.test(link);
          const Title = current ? "h1" : "p";
          return (
            <div
              key={b.id ?? i}
              className={`${heroClass} hero-slide w-full flex-none`}
              data-active={current}
              role="group"
              aria-roledescription="slide"
              aria-label={t("bannerDot", { index: i + 1 })}
              aria-hidden={!current || undefined}
              inert={!current || undefined}
            >
              {b.imageUrl && (
                <div className="hero-photo" aria-hidden="true">
                  <Image
                    src={b.imageUrl}
                    alt=""
                    fill
                    priority={i === 0}
                    sizes="(max-width: 900px) 100vw, 760px"
                    className="object-cover"
                  />
                </div>
              )}
              {/* The rise-in animation plays when the slide becomes active (the class is added then). */}
              <div className={`${current ? "hero-in " : ""}relative flex flex-col gap-4 pb-5`}>
                <span className="text-[13px] font-bold tracking-[0.14em] uppercase">{t("eyebrow")}</span>
                <Title className={titleClass}>{b.title}</Title>
                {b.subtitle && <p className="m-0 max-w-[46ch] text-[15px] opacity-90">{b.subtitle}</p>}
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
            </div>
          );
        })}
      </div>
      {count > 1 && (
        <div className="absolute bottom-6 left-[22px] flex gap-1.5 min-[561px]:left-9">
          {banners.map((b, i) => (
            <button
              key={b.id ?? i}
              type="button"
              onClick={() => setIndex(i)}
              aria-label={t("bannerDot", { index: i + 1 })}
              aria-current={i === active ? "true" : undefined}
              className={`relative h-2 overflow-hidden rounded-full border-0 bg-white/50 p-0 transition-[width] duration-300 ${
                i === active ? "w-6" : "w-2"
              }`}
            >
              {i === active && <span key={active} className="hero-dot-fill absolute inset-0 rounded-full bg-orange" />}
            </button>
          ))}
        </div>
      )}
    </section>
  );
}
