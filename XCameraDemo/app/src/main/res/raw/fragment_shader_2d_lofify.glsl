precision mediump float; //指定默认精度

varying vec2 vTextureCoord;
varying vec2 vExtraTextureCoord;

uniform sampler2D uTexture;
uniform sampler2D uExtraTexture;

//Lofify https://www.shadertoy.com/view/XdKGWG
uniform vec2 uiResolution;
uniform float uiGlobalTime;

#define STEPS 5.
#define STEP_DIFF (1. / STEPS)
#define LINE_THRESH (STEP_DIFF - 0.05)
#define GREEN_TOLERANCE 0.1

bool isEven(in float v) {
    return mod(v, 2.) == 0.;
}

float quantize(in vec3 tex) {
    float col = 0.2126 * tex.r + 0.7152 * tex.g + 0.0722 * tex.b;
    col = ceil(col * STEPS) / STEPS;
    return col;
}

float dither(in float col, in vec2 fragCoord) {
    float r3 = mod(col / STEP_DIFF, 3.);
    if(r3 == 0.) {
        return col;
    } else if(r3 == 1.) {
        if(isEven(fragCoord.x + fragCoord.y)) {
        	return col + STEP_DIFF;
    	} else {
        	return col - STEP_DIFF;
    	}
    } else {
        if(isEven(fragCoord.x + 0.5) && isEven(fragCoord.y + 0.5)) {
        	return col - STEP_DIFF;
    	} else {
        	return col + STEP_DIFF;
    	}
    }
}

bool isGreenPixel(vec3 tex) {
    vec3 green = vec3(13., 163., 37.) * (1. / 255.);
    vec3 ut = tex - (green + GREEN_TOLERANCE);
    vec3 dt = tex - (green - GREEN_TOLERANCE);
    return ut.x < 0. && ut.y < 0. && ut.z < 0. && dt.x > 0. && dt.y > 0. && dt.z > 0.;
}

vec3 lofify_ext(vec2 uv, vec2 uvStep, vec2 fragCoord) {

    vec3 tex = texture2D(uTexture, uv).xyz;
    vec3 texr = texture2D(uTexture, uv + vec2(uvStep.x, 0.)).xyz;
    vec3 texl = texture2D(uTexture, uv + vec2(-uvStep.x, 0.)).xyz;
    vec3 texu = texture2D(uTexture, uv + vec2(0., -uvStep.y)).xyz;
    vec3 texd = texture2D(uTexture, uv + vec2(0., uvStep.y)).xyz;

    float c = quantize(tex);
    float cr = quantize(texr);
    float cl = quantize(texl);
    float cu = quantize(texu);
    float cd = quantize(texd);

    bool thresh = (cr - c >= LINE_THRESH
    	|| cd - c >= LINE_THRESH
        || cl - c >= LINE_THRESH
        || cu - c >= LINE_THRESH)
        && isEven(c / STEP_DIFF);

    bool green = isGreenPixel(texr)
        || isGreenPixel(texd)
        || isGreenPixel(texu)
        || isGreenPixel(texl);

    if (thresh || green) {
        return vec3(0.);
    } else {
        return vec3(dither(c, fragCoord));
    }
}

vec3 lofify(vec2 uv, vec2 uvStep, vec2 fragCoord) {

    vec3 tex = texture2D(uExtraTexture, uv).xyz;
    vec3 texr = texture2D(uExtraTexture, uv + vec2(uvStep.x, 0.)).xyz;
    vec3 texl = texture2D(uExtraTexture, uv + vec2(-uvStep.x, 0.)).xyz;
    vec3 texu = texture2D(uExtraTexture, uv + vec2(0., -uvStep.y)).xyz;
    vec3 texd = texture2D(uExtraTexture, uv + vec2(0., uvStep.y)).xyz;

    float c = quantize(tex);
    float cr = quantize(texr);
    float cl = quantize(texl);
    float cu = quantize(texu);
    float cd = quantize(texd);

    bool thresh = (cr - c >= LINE_THRESH
    	|| cd - c >= LINE_THRESH
        || cl - c >= LINE_THRESH
        || cu - c >= LINE_THRESH)
        && isEven(c / STEP_DIFF);

    bool green = isGreenPixel(texr)
        || isGreenPixel(texd)
        || isGreenPixel(texu)
        || isGreenPixel(texl);

    if(thresh || green) {
        return vec3(0.);
    } else {
        return vec3(dither(c, fragCoord));
    }
}

void main() {
    vec2 uvStep = 1. / uiResolution.xy;
    vec2 uv = vTextureCoord;
    vec2 extra_uv = vExtraTextureCoord;
    vec2 fragCoord = vTextureCoord * uiResolution.xy;
    vec2 extra_fragCoord = vExtraTextureCoord * uiResolution.xy;

    vec3 fg = lofify_ext(uv, uvStep, fragCoord);
    vec3 bg = lofify(extra_uv + uiGlobalTime * vec2(0., 0.1), uvStep, extra_fragCoord);

    vec3 fcol = isGreenPixel(texture2D(uTexture, uv).xyz) ? bg : fg;

	gl_FragColor = vec4(fcol, 1.0);
}