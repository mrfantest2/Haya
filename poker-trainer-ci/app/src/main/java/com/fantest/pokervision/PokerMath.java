package com.fantest.pokervision;

import java.util.*;

public final class PokerMath {
    private PokerMath() {}

    public static final class Card {
        public final int rank;
        public final int suit;
        public Card(int rank, int suit) { this.rank = rank; this.suit = suit; }
        public String code() { return rankText(rank) + suitCode(suit); }
        public String pretty() { return rankText(rank) + suitSymbol(suit); }
        @Override public boolean equals(Object o) { return o instanceof Card && ((Card)o).rank == rank && ((Card)o).suit == suit; }
        @Override public int hashCode() { return rank * 10 + suit; }
    }

    public static final class Result {
        public final double win, tie, lose, equity;
        public final String currentHand, draws;
        public final int outs, simulations;
        Result(double win, double tie, double lose, double equity, String currentHand, String draws, int outs, int simulations) {
            this.win = win; this.tie = tie; this.lose = lose; this.equity = equity;
            this.currentHand = currentHand; this.draws = draws; this.outs = outs; this.simulations = simulations;
        }
    }

    private static final class HandValue implements Comparable<HandValue> {
        final int category;
        final int[] kickers;
        HandValue(int category, int... kickers) { this.category = category; this.kickers = kickers; }
        @Override public int compareTo(HandValue o) {
            if (category != o.category) return Integer.compare(category, o.category);
            int n = Math.min(kickers.length, o.kickers.length);
            for (int i = 0; i < n; i++) if (kickers[i] != o.kickers[i]) return Integer.compare(kickers[i], o.kickers[i]);
            return Integer.compare(kickers.length, o.kickers.length);
        }
    }

    public static Result calculate(List<Card> hole, List<Card> board, int opponents, int simulations, long seed) {
        if (hole.size() != 2) throw new IllegalArgumentException("Exactly two hole cards are required");
        if (board.size() > 5) throw new IllegalArgumentException("Board cannot exceed five cards");
        if (opponents < 1 || opponents > 9) throw new IllegalArgumentException("Opponents must be 1-9");
        Set<Card> known = new HashSet<>(); known.addAll(hole); known.addAll(board);
        if (known.size() != hole.size() + board.size()) throw new IllegalArgumentException("Duplicate cards detected");
        int need = opponents * 2 + (5 - board.size());
        if (52 - known.size() < need) throw new IllegalArgumentException("Not enough cards remaining");

        List<Card> base = deck(); base.removeAll(known);
        Random rng = new Random(seed);
        int wins = 0, ties = 0, losses = 0;
        double equityShares = 0.0;
        ArrayList<Card> shuffled = new ArrayList<>(base);
        ArrayList<Card> finalBoard = new ArrayList<>(5);
        ArrayList<Card> heroSeven = new ArrayList<>(7);
        ArrayList<Card> oppSeven = new ArrayList<>(7);

        for (int sim = 0; sim < simulations; sim++) {
            Collections.shuffle(shuffled, rng);
            int idx = 0;
            finalBoard.clear(); finalBoard.addAll(board);
            while (finalBoard.size() < 5) finalBoard.add(shuffled.get(idx++));
            heroSeven.clear(); heroSeven.addAll(hole); heroSeven.addAll(finalBoard);
            HandValue hero = best(heroSeven);
            int tiedOpponents = 0;
            boolean beaten = false;
            for (int p = 0; p < opponents; p++) {
                Card a = shuffled.get(idx++), b = shuffled.get(idx++);
                oppSeven.clear(); oppSeven.add(a); oppSeven.add(b); oppSeven.addAll(finalBoard);
                int cmp = best(oppSeven).compareTo(hero);
                if (cmp > 0) beaten = true;
                else if (cmp == 0) tiedOpponents++;
            }
            if (beaten) losses++;
            else if (tiedOpponents > 0) { ties++; equityShares += 1.0 / (tiedOpponents + 1.0); }
            else { wins++; equityShares += 1.0; }
        }

        List<Card> currentlyKnown = new ArrayList<>(); currentlyKnown.addAll(hole); currentlyKnown.addAll(board);
        String current = currentlyKnown.size() >= 5 ? handName(best(currentlyKnown).category) : "Pre-flop";
        String draws = drawSummary(currentlyKnown);
        int outs = countImprovingOuts(hole, board);
        double denom = simulations;
        return new Result(100.0*wins/denom, 100.0*ties/denom, 100.0*losses/denom,
                100.0*equityShares/denom, current, draws, outs, simulations);
    }

    private static int countImprovingOuts(List<Card> hole, List<Card> board) {
        if (board.size() >= 5) return 0;
        List<Card> known = new ArrayList<>(); known.addAll(hole); known.addAll(board);
        if (known.size() < 5) return drawOutsApprox(known);
        HandValue now = best(known);
        Set<Card> used = new HashSet<>(known);
        int count = 0;
        for (Card c : deck()) {
            if (used.contains(c)) continue;
            ArrayList<Card> next = new ArrayList<>(known); next.add(c);
            if (best(next).category > now.category) count++;
        }
        return count;
    }

    private static int drawOutsApprox(List<Card> cards) {
        int[] suits = new int[4];
        boolean[] ranks = new boolean[15];
        for (Card c : cards) { suits[c.suit]++; ranks[c.rank] = true; if (c.rank == 14) ranks[1] = true; }
        int best = 0;
        for (int s : suits) if (s == 4) best = Math.max(best, 9);
        for (int start = 1; start <= 10; start++) {
            int missing = 0;
            for (int r = start; r < start + 5; r++) if (!ranks[r]) missing++;
            if (missing == 1) best = Math.max(best, 4);
        }
        return best;
    }

    private static String drawSummary(List<Card> cards) {
        if (cards.size() < 4) return "No board draw yet";
        ArrayList<String> out = new ArrayList<>();
        int[] suits = new int[4]; boolean[] ranks = new boolean[15];
        for (Card c : cards) { suits[c.suit]++; ranks[c.rank] = true; if (c.rank == 14) ranks[1] = true; }
        for (int s : suits) if (s == 4) { out.add("Flush draw"); break; }
        for (int start=1; start<=10; start++) {
            int missing=0; for(int r=start;r<start+5;r++) if(!ranks[r]) missing++;
            if(missing==1) { out.add("Straight draw"); break; }
        }
        return out.isEmpty() ? "No major draw detected" : String.join(" + ", out);
    }

    private static HandValue best(List<Card> cards) {
        if (cards.size() < 5) throw new IllegalArgumentException("Need at least five cards");
        HandValue best = null;
        int n = cards.size();
        for (int a=0;a<n-4;a++) for(int b=a+1;b<n-3;b++) for(int c=b+1;c<n-2;c++) for(int d=c+1;d<n-1;d++) for(int e=d+1;e<n;e++) {
            HandValue v = five(cards.get(a), cards.get(b), cards.get(c), cards.get(d), cards.get(e));
            if (best == null || v.compareTo(best) > 0) best = v;
        }
        return best;
    }

    private static HandValue five(Card... cs) {
        int[] count = new int[15]; int[] suits = new int[4];
        for(Card c:cs){count[c.rank]++; suits[c.suit]++;}
        boolean flush=false; for(int x:suits) if(x==5) flush=true;
        int straightHigh=0;
        for(int high=14;high>=5;high--) {
            boolean ok=true; for(int r=high;r>high-5;r--) if(count[r]==0) ok=false;
            if(ok){straightHigh=high;break;}
        }
        if(straightHigh==0 && count[14]>0 && count[2]>0 && count[3]>0 && count[4]>0 && count[5]>0) straightHigh=5;
        if(flush && straightHigh>0) return new HandValue(8, straightHigh);
        int four=0, three=0; ArrayList<Integer> pairs=new ArrayList<>(), singles=new ArrayList<>();
        for(int r=14;r>=2;r--){ if(count[r]==4) four=r; else if(count[r]==3) { if(three==0) three=r; else pairs.add(r); } else if(count[r]==2) pairs.add(r); else if(count[r]==1) singles.add(r); }
        if(four>0) return new HandValue(7, four, singles.get(0));
        if(three>0 && !pairs.isEmpty()) return new HandValue(6, three, pairs.get(0));
        int[] desc = descendingRanks(count);
        if(flush) return new HandValue(5, desc);
        if(straightHigh>0) return new HandValue(4, straightHigh);
        if(three>0) return new HandValue(3, three, singles.get(0), singles.get(1));
        if(pairs.size()>=2) return new HandValue(2, pairs.get(0), pairs.get(1), singles.get(0));
        if(pairs.size()==1) return new HandValue(1, pairs.get(0), singles.get(0), singles.get(1), singles.get(2));
        return new HandValue(0, desc);
    }

    private static int[] descendingRanks(int[] count) {
        int[] a=new int[5]; int i=0; for(int r=14;r>=2;r--) for(int k=0;k<count[r];k++) a[i++]=r; return a;
    }

    public static String handNameFor(List<Card> cards) { return cards.size() < 5 ? "Pre-flop" : handName(best(cards).category); }
    private static String handName(int c) {
        switch(c){case 8:return "Straight Flush";case 7:return "Four of a Kind";case 6:return "Full House";case 5:return "Flush";case 4:return "Straight";case 3:return "Three of a Kind";case 2:return "Two Pair";case 1:return "One Pair";default:return "High Card";}
    }
    public static List<Card> deck(){ArrayList<Card>d=new ArrayList<>(52);for(int r=2;r<=14;r++)for(int s=0;s<4;s++)d.add(new Card(r,s));return d;}
    public static String rankText(int r){ if(r<=10)return String.valueOf(r); if(r==11)return"J"; if(r==12)return"Q"; if(r==13)return"K"; return"A"; }
    public static String suitSymbol(int s){return s==0?"♠":s==1?"♥":s==2?"♦":"♣";}
    public static String suitCode(int s){return s==0?"S":s==1?"H":s==2?"D":"C";}
    public static int suitFromChar(char c){c=Character.toUpperCase(c);if(c=='S'||c=='♠')return 0;if(c=='H'||c=='♥')return 1;if(c=='D'||c=='♦')return 2;if(c=='C'||c=='♣')return 3;return -1;}
    public static int rankFromText(String s){s=s.toUpperCase(Locale.US);if(s.equals("A"))return 14;if(s.equals("K"))return 13;if(s.equals("Q"))return 12;if(s.equals("J"))return 11;try{int n=Integer.parseInt(s);return n>=2&&n<=10?n:-1;}catch(Exception e){return-1;}}
}
