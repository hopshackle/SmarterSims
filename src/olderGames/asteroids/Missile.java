package olderGames.asteroids;

import math.Vector2d;

import java.awt.*;

public class Missile extends GameObject {

    int ttl;

    public Missile(Vector2d s, Vector2d v, int ttl, int rad) {
        super(s, v);
        this.ttl = ttl;
        // missileTTL = 100;
        r = rad;
    }

    @Override
    public void update(AsteroidsGameState gameState) {
        // System.out.println("Updating missile: " + this);
        if (!dead()) {
            s.add(v);
            ttl--;
        }
    }

    public void update() {
        // System.out.println("Updating missile: " + this);
        if (!dead()) {
            s.add(v);
            ttl--;
        }
    }

    @Override
    public void draw(Graphics2D g) {
        g.setColor(Color.red);
        g.fillOval((int) (s.getX()-r), (int) (s.getY()-r), (int) r * 2, (int) r * 2);
    }

    public boolean dead() {
        return ttl <= 0;
    }

    public void hit() {
        // kill it by setting ttl to zero
        ttl = 0;
    }

    public String toString() {
        return ttl + " :> " + s;
    }


    public Missile copy() {
        Missile missile = new Missile(s, v, ttl, (int) r);
        return missile;
    }
}