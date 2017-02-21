package com.gotye.bibo.filter;

import android.content.Context;
import android.graphics.PointF;

import com.gotye.bibo.R;
import com.gotye.bibo.util.LogUtil;


public class FilterManager {

    private static final String TAG = "FilterManager";
    private static int mCurveIndex;
    private static int[] mCurveArrays = new int[]{
            R.raw.cross_1, R.raw.cross_2, R.raw.cross_3, R.raw.cross_4, R.raw.cross_5,
            R.raw.cross_6, R.raw.cross_7, R.raw.cross_8, R.raw.cross_9, R.raw.cross_10,
            R.raw.cross_11,
    };

    private FilterManager() {
    }

    public static IFilter getCameraFilter(FilterType filterType, Context context, float... params) {
        switch (filterType) {
            case Normal:
            default:
                return new CameraFilter(context);
            case Blend:
                return new CameraFilterBlend(context, R.drawable.mask);
            case SoftLight:
                return new CameraFilterBlendSoftLight(context, R.drawable.mask);
            case ToneCurve:
                if (params != null && params.length > 0) {
                    mCurveIndex = (int) (params[0] * 11f);
                    if (mCurveIndex > mCurveArrays.length - 1)
                        mCurveIndex = mCurveArrays.length - 1;
                } else {
                    mCurveIndex++;
                    if (mCurveIndex > mCurveArrays.length - 1) {
                        mCurveIndex = 0;
                    }
                }

                return new CameraFilterToneCurve(
                        context,
                        context.getResources().
                                openRawResource(mCurveArrays[mCurveIndex]));
            case GaussianBlur:
                if (params != null && params.length > 0)
                    return new CameraFilterGaussianBlur(context, params[0]);
                else
                    return new CameraFilterGaussianBlur(context, 3f);
            case Gray:
                return new CameraFilterGrayScale(context);
            case BeautyFace:
                if (params != null && params.length > 0) {
                    return new ImageFilterThreeInput(context,
                            new CameraFilterBilateralBlur(context, params[0], params[1]),
                            new CameraFilterCannyEdgeDetection(context),
                            new CameraFilter(context),
                            new ImageFilterBeautify(context));
                } else {
                    return new ImageFilterThreeInput(context,
                            new CameraFilterBilateralBlur(context, 6f, 3f),
                            new CameraFilterCannyEdgeDetection(context),
                            new CameraFilter(context),
                            new ImageFilterBeautify(context));
                }
            case BeautyFaceTest:
                if (params != null && params.length > 0) {
                    return new ImageFilterThreeInput(context,
                            new CameraFilterBilateralBlur(context, params[0], params[1]),
                            new CameraFilterCannyEdgeDetection(context),
                            new CameraFilter(context),
                            new ImageFilterBeautifyTest(context));
                } else {
                    return new ImageFilterThreeInput(context,
                            new CameraFilterBilateralBlur(context, 6f, 3f),
                            new CameraFilterCannyEdgeDetection(context),
                            new CameraFilter(context),
                            new ImageFilterBeautifyTest(context));
                }
            case AllinOne:
                if (params != null && params.length > 0)
                    return new CameraFilterAllinOne(context, params[0], params[1]);
                else
                    return new CameraFilterAllinOne(context, 6f, 3f);
            case BeautyFaceWu:
                if (params != null && params.length > 0) //todo 出现一个 越界异常时为什么
                    if (params.length == 1) {
                        LogUtil.error(TAG, "BeautyFaceWu 数组异常");
                        return new CameraFilterBeautifyFaceWu(context, params[0], params[1]);

                    } else
                        return new CameraFilterBeautifyFaceWu(context);
            case Brightness:
                return new CameraFilterBrightness(context, 0.1f);
            case Saturation:
                return new CameraFilterSaturation(context, 1.1f);
            case Sharpen:
                if (params != null && params.length > 0)
                    return new CameraFilterSharpen(context, params[0]);
                else
                    return new CameraFilterSharpen(context);
            case CannyEdgeDetection:
                return new CameraFilterCannyEdgeDetection(context);
            case FaceColor:
                if (params != null && params.length > 0)
                    return new CameraFilterFaceColor(context, params[0], params[1], params[2]);
                else
                    return new CameraFilterFaceColor(context);
            case Cartoon:
                return new CameraFilterCartoon(context);
            case Toon:
                if (params != null && params.length > 0)
                    return new CameraFilterToon(context, params[0], params[1], false);
                else
                    return new CameraFilterToon(context);
            case SmoothToon:
                if (params != null && params.length > 0)
                    return new CameraFilterSmoothToon(context, params[0], params[1], params[2]);
                else
                    return new CameraFilterSmoothToon(context);
            case White:
                return new CameraFilterWhite(context);
            case MosaicBlur:
                return new CameraFilterMosaicBlur(context);
            case BilateralBlur:
                if (params != null && params.length > 0)
                    return new CameraFilterBilateralBlur(context, params[0], params[1]);
                else
                    return new CameraFilterBilateralBlur(context, 6f, 3f);
            case GaussianSelectiveBlur:
                float blurRadius = 4.0f;
                float excludeCircleRadius = 0.2f;
                PointF excludePoint1 = new PointF(0.5f, 0.0f);
                PointF excludePoint2 = new PointF(0.5f, 1.0f);
                float excludeBlurSize = 0.1f;
                return new CameraFilterGaussianSelectiveBlur(context,
                        blurRadius, excludeCircleRadius,
                        excludePoint1, excludePoint2, excludeBlurSize);
            case Cracked:
                return new CameraFilterCracked(context);
            case Legofield:
                return new CameraFilterLegofied(context);
            case BasicDeform:
                return new CameraFilterBasicDeform(context);
            case Cartoonish:
                return new CameraFilterCartoonish(context);
            case EdgeDetectiondFdx:
                return new CameraFilterEdgeDetectiondFdx(context);
            case Pixelize:
                return new CameraFilterPixelize(context);
            case NoiseContour:
                return new CameraFilterNoiseContour(context);
            case Lofify:
                return new CameraFilterLofify(context, R.drawable.bg1);
            case BritneyCartoon:
                return new CameraFilterBritneyCartoon(context);
            case BulgeDistortion:
                if (params != null && params.length > 0) {
                    float radius, scale;
                    PointF center;
                    radius = params[0];
                    scale = params[1];
                    center = new PointF(params[2], params[3]);
                    return new CameraFilterBulgeDistortion(context, radius, scale, center);
                } else
                    return new CameraFilterBulgeDistortion(context);
            case PinchDistortion:
                if (params != null && params.length > 0) {
                    float radius, scale;
                    PointF center;
                    radius = params[0];
                    scale = params[1];
                    center = new PointF(params[2], params[3]);
                    return new CameraFilterPinchDistortion(context, radius, scale, center);
                } else
                    return new CameraFilterPinchDistortion(context);
            case StretchDistortion:
                return new CameraFilterStretchDistortion(context);
            case AsciiArt:
                if (params != null && params.length > 0) {
                    return new CameraFilterAsciiArt(context, params[0] > 0.5);
                } else {
                    return new CameraFilterAsciiArt(context);
                }
            case AsciiArt2:
                return new CameraFilterAsciiArt2(context);
            case ChromaKey:
                if (params != null && params.length > 0)
                    return new CameraFilterChromaKey(context, 0f, 1f, 0f, R.drawable.bg1, params[0], params[1]);
                else
                    return new CameraFilterChromaKey(context, 0f, 1f, 0f, R.drawable.bg1);
            case ChromaKeyBlend:
                if (params != null && params.length > 0)
                    return new CameraFilterChromaKeyBlend(context, 0f, 1f, 0f, R.drawable.bg1, params[0], params[1]);
                else
                    return new CameraFilterChromaKeyBlend(context, 0f, 1f, 0f, R.drawable.bg1);

        }
    }

    public static IFilter getImageFilter(FilterType filterType, Context context, float... params) {
        switch (filterType) {
            case Normal:
            default:
                return new ImageFilter(context);
            case Gray:
                return new ImageFilterGrayScale(context);
            case Blend:
                return new ImageFilterBlend(context, R.drawable.mask);
            case SoftLight:
                return new ImageFilterBlendSoftLight(context, R.drawable.mask);
            case ToneCurve:
                if (params != null && params.length > 0) {
                    mCurveIndex = (int) (params[0] * 11f);
                    if (mCurveIndex > mCurveArrays.length - 1)
                        mCurveIndex = mCurveArrays.length - 1;
                } else {
                    mCurveIndex++;
                    if (mCurveIndex > mCurveArrays.length - 1) {
                        mCurveIndex = 0;
                    }
                }

                return new ImageFilterToneCurve(
                        context,
                        context.getResources().
                                openRawResource(mCurveArrays[mCurveIndex]));
            case GaussianBlur:
                if (params != null && params.length > 0)
                    return new ImageFilterGaussianBlur(context, params[0]);
                else
                    return new ImageFilterGaussianBlur(context, 3f);
            case BilateralBlur:
                if (params != null && params.length > 0)
                    return new ImageFilterBilateralBlur(context, params[0], params[1]);
                else
                    return new ImageFilterBilateralBlur(context, 6f, 3f);
            case BeautyFace:
                return new ImageFilterThreeInput(context,
                        new ImageFilterBilateralBlur(context, 6f, 3f),
                        new ImageFilterCannyEdgeDetection(context),
                        new ImageFilter(context),
                        new ImageFilterBeautify(context));
            case BeautyFaceTest:
                return new ImageFilterThreeInput(context,
                        new ImageFilterBilateralBlur(context, 6f, 3f),
                        new ImageFilterCannyEdgeDetection(context),
                        new ImageFilter(context),
                        new ImageFilterBeautifyTest(context));
            case AllinOne:
                if (params != null && params.length > 0)
                    return new ImageFilterAllinOne(context, params[0], params[1]);
                else
                    return new ImageFilterAllinOne(context, 6f, 3f);
            case BeautyFaceWu:
                if (params != null && params.length > 0)
                    return new ImageFilterBeautifyFaceWu(context, params[0], params[1]);
                else
                    return new ImageFilterBeautifyFaceWu(context);
            case Brightness:
                return new ImageFilterBrightness(context, 0.1f);
            case Saturation:
                return new ImageFilterSaturation(context, 1.1f);
            case Sharpen:
                if (params != null && params.length > 0)
                    return new ImageFilterSharpen(context, params[0]);
                else
                    return new ImageFilterSharpen(context);
            case CannyEdgeDetection:
                return new ImageFilterCannyEdgeDetection(context);
            case Cartoon:
                return new ImageFilterCartoon(context);
            case Toon:
                if (params != null && params.length > 0)
                    return new ImageFilterToon(context, params[0], params[1], false);
                else
                    return new ImageFilterToon(context);
            case SmoothToon:
                if (params != null && params.length > 0)
                    return new ImageFilterSmoothToon(context, params[0], params[1], params[2]);
                else
                    return new ImageFilterSmoothToon(context);
            case White:
                if (params != null && params.length > 0)
                    return new ImageFilterWhite(context, params[0], params[1]);
                else
                    return new ImageFilterWhite(context);
            case MosaicBlur:
                if (params != null && params.length > 0)
                    return new ImageFilterMosaicBlur(context, 8f, params[0]);
                else
                    return new ImageFilterMosaicBlur(context);
            case FaceColor:
                if (params != null && params.length > 0)
                    return new ImageFilterFaceColor(context, params[0], params[1], params[2]);
                else
                    return new ImageFilterFaceColor(context);
            case GaussianSelectiveBlur:
                if (params != null && params.length > 0) {
                    if (params[0] > 0.0f) {
                        float blurRadius = 4.0f;
                        float excludeCircleRadius = params[2];
                        PointF excludePoint1, excludePoint2;
                        if (params[0] > 0.3f) {
                            excludePoint1 = new PointF(params[1], 0.0f);
                            excludePoint2 = new PointF(params[1], 1.0f);
                        } else {
                            excludePoint1 = new PointF(0.0f, params[1]);
                            excludePoint2 = new PointF(1.0f, params[1]);
                        }

                        float excludeBlurSize = params[2] / 2f;
                        return new ImageFilterGaussianSelectiveBlur(context,
                                blurRadius, excludeCircleRadius,
                                excludePoint1, excludePoint2, excludeBlurSize);
                    } else {
                        float blurRadius = 4.0f;
                        float excludeCircleRadius = params[1];
                        PointF excludeCirclePoint = new PointF(0.5f, 0.5f);
                        float excludeBlurSize = Math.min(params[1], params[2]);
                        return new ImageFilterGaussianSelectiveBlur(context,
                                blurRadius, excludeCircleRadius,
                                excludeCirclePoint, excludeBlurSize);
                    }
                } else {
                    return new ImageFilterGaussianSelectiveBlur(context);
                }
            case Cracked:
                return new ImageFilterCracked(context);
            case Legofield:
                return new ImageFilterLegofied(context);
            case BasicDeform:
                return new ImageFilterBasicDeform(context);
            case Cartoonish:
                return new ImageFilterCartoonish(context);
            case EdgeDetectiondFdx:
                return new ImageFilterEdgeDetectiondFdx(context);
            case Pixelize:
                return new ImageFilterPixelize(context);
            case NoiseContour:
                return new ImageFilterNoiseContour(context);
            case Lofify:
                return new ImageFilterLofify(context, R.drawable.bg1);
            case BritneyCartoon:
                return new ImageFilterBritneyCartoon(context);
            case BulgeDistortion:
                if (params != null && params.length > 0) {
                    float radius, scale;
                    PointF center;
                    radius = params[0];
                    scale = params[1];
                    center = new PointF(params[2], params[3]);
                    return new ImageFilterBulgeDistortion(context, radius, scale, center);
                } else
                    return new ImageFilterBulgeDistortion(context);
            case PinchDistortion:
                if (params != null && params.length > 0) {
                    float radius, scale;
                    PointF center;
                    radius = params[0];
                    scale = params[1];
                    center = new PointF(params[2], params[3]);
                    return new ImageFilterPinchDistortion(context, radius, scale, center);
                } else
                    return new ImageFilterPinchDistortion(context);
            case StretchDistortion:
                return new ImageFilterStretchDistortion(context);
            case AsciiArt:
                if (params != null && params.length > 0) {
                    return new ImageFilterAsciiArt(context, params[0] > 0.5);
                } else {
                    return new ImageFilterAsciiArt(context);
                }

            case AsciiArt2:
                return new ImageFilterAsciiArt2(context);
        }
    }

    public static enum FilterType {
        Normal,
        Blend,
        SoftLight,
        ToneCurve,
        MotionBlur,
        GaussianBlur,
        Gray,
        MosaicBlur,
        BilateralBlur,
        BeautyFace,
        BeautyFaceTest,
        AllinOne,
        BeautyFaceWu,
        Brightness,
        Saturation,
        Sharpen,
        FaceColor,
        Cartoon,
        Toon,
        SmoothToon,
        White,
        CannyEdgeDetection,
        GaussianSelectiveBlur,
        Cracked,
        Legofield,
        BasicDeform,
        Cartoonish,
        EdgeDetectiondFdx,
        Pixelize,
        NoiseContour,
        Lofify,
        BritneyCartoon,
        BulgeDistortion,
        PinchDistortion,
        StretchDistortion,
        AsciiArt,
        AsciiArt2,
        ChromaKey,
        ChromaKeyBlend;

        private FilterType() {
        }

        public String canonicalForm() {
            return this.name().toLowerCase();
        }

        public static FilterType fromCanonicalForm(String canonical) {
            return (FilterType) valueOf(FilterType.class, canonical/*.toUpperCase()*/);
        }

        public boolean isSingleFilter() {
            boolean ret = false;
            if (this.name().equals("Normal") ||
                    this.name().equals("Gray") ||
                    this.name().equals("Blend") ||
                    this.name().equals("SoftLight") ||
                    this.name().equals("ToneCurve") ||
                    this.name().equals("BeautyFaceWu") ||
                    this.name().equals("White") ||
                    this.name().equals("Cartoon") ||
                    this.name().equals("Toon") ||
                    this.name().equals("Sharpen") ||
                    this.name().equals("MosaicBlur") ||
                    this.name().equals("FaceColor") ||
                    this.name().equals("Cracked") ||
                    this.name().equals("Legofield") ||
                    this.name().equals("BasicDeform") ||
                    this.name().equals("Cartoonish") ||
                    this.name().equals("EdgeDetectiondFdx") ||
                    this.name().equals("Pixelize") ||
                    this.name().equals("NoiseContour") ||
                    this.name().equals("Lofify") ||
                    this.name().equals("BritneyCartoon") ||
                    this.name().contains("Distortion") ||
                    this.name().contains("AsciiArt") ||
                    this.name().contains("ChromaKey")) {
                ret = true;
            }

            return ret;
        }
    }
}
