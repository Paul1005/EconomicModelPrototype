package economicModel;

import java.util.ArrayList;

//TODO: incorporate inflation somehow, right now incentive is to keep price level at 1
public class ASADModel {
    public double longRunAggregateSupply;
    public double shortRunAggregateSupplyCurve;
    public double taxes;
    public double mpc;
    public double mpi;
    public double mps;
    public double reserveRequirement;
    public double ownedBonds;
    public double moneySupply;
    public double GConstant;
    public double IConstant;
    public double G;
    public double outputGap;
    public double C; // Should maybe be affected by inflation
    public double aggregateDemandOutputCurve;
    public double equilibriumOutput;
    public double I;

    private double taxMultiplier;
    private double spendingMultiplier;

    //TODO: these should somehow be affected by inflation
    public double govtBalance;
    private double overallGovtBalance;
    private double overallGovtBalanceWInterest;
    public double publicBalance;
    private double overallPublicBalance;
    private double overallPublicBalanceWInterest;
    public double overallGovtBalanceInflationAdjusted;
    public double overallPublicBalanceInflationAdjusted;

    public double publicDebtInterest;
    public double govtDebtInterest;

    //public  int debtCycles; // number of cycles we use to pay of debt
    public int debtRepaymentAmount; // min debt repayment required
    private ArrayList<Double> govtDebts = new ArrayList<>();
    private ArrayList<Double> publicDebts = new ArrayList<>();

    double growth;
    double overallGrowth;
    int cyclesRun;
    private double originalOutput = 0;
    private double previousOutput = 0;

    public double priceLevel;
    private double previousPriceLevel;
    private double originalPriceLevel;
    public double overallInflation;
    public double inflation;


    /**
     * Find investment based on interest rate, IConstant, and mpi. Is the inverse(swap x and y) of the equation below. \frac{a\sqrt{\sqrt{x^{2}+4}-x}}{\sqrt{2}\cdot b}
     *
     * @param interestRate
     * @return
     */
    private double investmentEquation(double interestRate) {
        return IConstant * Math.sqrt(Math.sqrt(Math.pow(interestRate, 2) + 4) - interestRate) / (Math.sqrt(2) * overallInflation);
    }

    /**
     * Find interest rate based on investment, IConstant, and mpi. Is the inverse(swap x and y) of the equation above. \frac{a^{4}-b^{4}\cdot x^{4}}{a^{2}\cdot b^{2}\cdot x^{2}}
     *
     * @param investmentRequired
     * @return
     */
    private double interestRateEquation(double investmentRequired) {
        return (Math.pow(IConstant, 4) - Math.pow(overallInflation, 4) * Math.pow(investmentRequired, 4)) / (Math.pow(IConstant, 2) * Math.pow(overallInflation, 2) * Math.pow(investmentRequired, 2));
    }

    /**
     * Find money supply based on interest rate and long run aggregate supply.
     *
     * @param interestRate
     * @return
     */
    private double moneySupplyEquation(double interestRate) {
        return -interestRate * longRunAggregateSupply + longRunAggregateSupply;
    }

    /**
     * Find the interest rate multiplier based on how fast your economy is growing, how large your debt is, and how large your economy is. \frac{\sqrt{x^{2}+4}-x}{\left(2\cdot a+a\cdot b\right)}
     *
     * @param totalAssets
     * @param currentBalance
     * @return
     */
    private double baseDebtInterestEquation(double totalAssets, double currentBalance) {
        return 1 / (2 * totalAssets + totalAssets * overallGrowth) * (Math.sqrt(Math.pow(currentBalance, 2) + 4) - currentBalance); // may not work in cases of negative assets or very negative growth
    }

    void runCycle() {
        taxMultiplier = -mpc / mps;
        spendingMultiplier = 1 / mps;

        moneySupply = ownedBonds / reserveRequirement; // find money supply based on bonds and reserve requirement
        double interestRate = (longRunAggregateSupply - moneySupply) / longRunAggregateSupply; // find interest rate based on current money supply
        I = investmentEquation(interestRate); // overall investment

        publicBalance = IConstant - I;
        if (publicBalance < 0) {
            publicDebtInterest = (baseDebtInterestEquation(IConstant, publicBalance) + interestRate) / 2; // might need a better equation for this
            takeOutLoan(publicDebts, publicBalance);
            overallPublicBalance += publicBalance;
            overallPublicBalanceWInterest = overallPublicBalance + overallPublicBalance * publicDebtInterest;
            servicePublicDebt();
        } else if (govtBalance > 0) {
            repayPublicLoan(publicBalance);
        }

        G = GConstant * spendingMultiplier; // overall government spending

        equilibriumOutput = C + taxes * taxMultiplier + G + I; // should equal LRAS when price is set to one

        priceLevel = (Math.sqrt(4 * C * longRunAggregateSupply + Math.pow(G, 2) + 2 * G * taxes * taxMultiplier + 4 * longRunAggregateSupply * I + Math.pow(taxes, 2) * Math.pow(taxMultiplier, 2)) + G + taxes * taxMultiplier) / (2 * longRunAggregateSupply); // find our equilibrium price level
        aggregateDemandOutputCurve = (C + I) / priceLevel + G + taxes * taxMultiplier; // this is the aggregate demand curve
        shortRunAggregateSupplyCurve = longRunAggregateSupply * priceLevel; // this is the short run aggregate supply curve

        outputGap = longRunAggregateSupply - equilibriumOutput; // find the output gap so that our price will be one

        govtBalance = taxes - GConstant;

        if (govtBalance < 0) {
            govtDebtInterest = (baseDebtInterestEquation(longRunAggregateSupply, govtBalance) + interestRate) / 2; // might need a better equation for this
            takeOutLoan(govtDebts, govtBalance);
            overallGovtBalance += govtBalance;
            overallGovtBalanceWInterest = overallGovtBalance + overallGovtBalance * govtDebtInterest;
            serviceGovtDebt();
        } else if (govtBalance > 0) {
            repayGovtLoan(govtBalance);
        }

        if (cyclesRun == 0) {
            originalOutput = equilibriumOutput;
            originalPriceLevel = priceLevel;
        } else {
            growth = equilibriumOutput / previousOutput;
            overallGrowth = equilibriumOutput / originalOutput;
            inflation = priceLevel / previousPriceLevel;
            overallInflation = priceLevel / originalPriceLevel;
        }
        previousOutput = equilibriumOutput;
        previousPriceLevel = priceLevel;

        cyclesRun++;
    }

    private void takeOutLoan(ArrayList<Double> debts, double balance) {
        debts.add(Math.abs(balance));
    }

    void changeReserveRequirements() {
        double investmentRequired = longRunAggregateSupply - C - G - taxes * taxMultiplier; // find how much investment we need
        double interestRate = interestRateEquation(investmentRequired); // find the new interest rate based on the investment we need.
        double newMoneySupply = moneySupplyEquation(interestRate); // find the money supply we need based on the new interest rate
        reserveRequirement *= (moneySupply / newMoneySupply); // determine the new reserve requirement based on the new and old money supply
    }

    void changeMoneySupply() {
        double investmentRequired = longRunAggregateSupply - C - G - taxes * taxMultiplier; // find how much investment we need
        double interestRate = interestRateEquation(investmentRequired); // find the new interest rate based on the investment we need.
        double newMoneySupply = moneySupplyEquation(interestRate); // find the money supply we need based on the new interest rate
        double gap = newMoneySupply - moneySupply; // determine how much more money we need
        double bondChange = gap * reserveRequirement; // determine how many more bonds we need to buy or sell
        ownedBonds += bondChange; // add the change in bonds
    }

    void changeSpending() {
        double spendingChange = outputGap / spendingMultiplier; // find the change in spending required
        GConstant += spendingChange; // add spending change to government spending
    }

    void changeTaxes() {
        double taxChange = outputGap / taxMultiplier; // find the change in taxes required
        if (taxes + taxChange <= 0) {
            System.out.println("can't cut taxes enough");
        } else {
            taxes += taxChange; // add tax change to total taxes
        }
    }

    // repay loans using surplus
    private void repayGovtLoan(double govtBalance) {
        overallGovtBalance -= govtBalance;
    }

    private void repayPublicLoan(double publicBalance) {
        overallPublicBalance -= publicBalance;
    }

    // overall debt servicing, might need to make these harsher
    private void serviceGovtDebt() {
        overallGovtBalanceWInterest = overallGovtBalance + overallGovtBalance * govtDebtInterest;
        GConstant -= (debtRepaymentAmount * govtDebtInterest);
        overallGovtBalanceWInterest -= (debtRepaymentAmount + debtRepaymentAmount * govtDebtInterest);
        overallGovtBalanceInflationAdjusted = overallGovtBalanceWInterest / priceLevel;
    }

    private void servicePublicDebt() {
        overallPublicBalanceWInterest = overallPublicBalance + overallPublicBalance * publicDebtInterest;
        C -= (debtRepaymentAmount * publicDebtInterest);
        overallPublicBalanceWInterest -= (debtRepaymentAmount + debtRepaymentAmount * publicDebtInterest);
        overallPublicBalanceInflationAdjusted = overallPublicBalanceWInterest / priceLevel;
    }
}
