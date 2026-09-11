package com.hyperring;

public class Spring {
    public float current;
    public float target;
    public float velocity = 0f;
    public float stiffness;
    public float damping;

    public Spring(float initial, float k, float dampingRatio) {
        this.current = initial;
        this.target = initial;
        setParameters(k, dampingRatio);
    }

    public void setParameters(float k, float dampingRatio) {
        this.stiffness = k;
        this.damping = (float) (2.0 * Math.sqrt(k) * dampingRatio);
    }

    public void setTarget(float t) {
        this.target = t;
    }

    public void snapTo(float val) {
        this.current = val;
        this.target = val;
        this.velocity = 0f;
    }

    private float getAcceleration(float pos, float vel) {
        return -stiffness * (pos - target) - damping * vel;
    }

    public boolean update(float dt) {
        float p1 = current;
        float v1 = velocity;
        float a1 = getAcceleration(p1, v1);

        float p2 = p1 + 0.5f * v1 * dt;
        float v2 = v1 + 0.5f * a1 * dt;
        float a2 = getAcceleration(p2, v2);

        float p3 = p1 + 0.5f * v2 * dt;
        float v3 = v1 + 0.5f * a2 * dt;
        float a3 = getAcceleration(p3, v3);

        float p4 = p1 + v3 * dt;
        float v4 = v1 + a3 * dt;
        float a4 = getAcceleration(p4, v4);

        float dpos = (dt / 6.0f) * (v1 + 2f * v2 + 2f * v3 + v4);
        float dvel = (dt / 6.0f) * (a1 + 2f * a2 + 2f * a3 + a4);

        current += dpos;
        velocity += dvel;

        if (target <= IslandConfig.cutoutRadius * 2.0f && target > 1.5f && current < target) {
            current = target;
            velocity = 0f;
            return false;
        }

        boolean isNormalized = (target <= 1.05f && target >= 0.0f);
        float posEpsilon = isNormalized ? 0.0005f : 0.25f;
        float velEpsilon = isNormalized ? 0.001f : 0.25f;

        if (Math.abs(velocity) < velEpsilon && Math.abs(current - target) < posEpsilon) {
            current = target;
            velocity = 0f;
            return false;
        }
        return true;
    }

    public boolean isMoving() {
        boolean isNormalized = (target <= 1.05f && target >= 0.0f);
        float posEpsilon = isNormalized ? 0.0005f : 0.25f;
        float velEpsilon = isNormalized ? 0.001f : 0.25f;
        return Math.abs(velocity) > velEpsilon || Math.abs(current - target) > posEpsilon;
    }

    public boolean isAtEquilibrium() {
        return !isMoving();
    }
}
