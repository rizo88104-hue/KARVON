import Image from "next/image";

/** Product photo on the grey placeholder background, or the placeholder text when there is no photo. */
export function ProductImage({
  src,
  alt,
  placeholder,
  sizes,
  priority,
  className = "",
}: {
  src?: string | null;
  alt: string;
  placeholder: string;
  sizes: string;
  priority?: boolean;
  className?: string;
}) {
  return (
    <div className={`relative grid place-items-center overflow-hidden bg-grey-bg text-[13px] text-muted ${className}`}>
      {src ? (
        <Image src={src} alt={alt} fill sizes={sizes} priority={priority} className="object-cover" />
      ) : (
        <span>{placeholder}</span>
      )}
    </div>
  );
}
