package connection;

import jason.JasonException;
import jason.NoValueException;
import jason.asSyntax.ASSyntax;
import jason.asSyntax.ListTerm;
import jason.asSyntax.ListTermImpl;
import jason.asSyntax.Literal;
import jason.asSyntax.NumberTerm;
import jason.asSyntax.StringTerm;
import jason.asSyntax.Term;
import jason.asSyntax.parser.ParseException;

import java.util.List;

import eis.iilang.Action;
import eis.iilang.Function;
import eis.iilang.Identifier;
import eis.iilang.Numeral;
import eis.iilang.Parameter;
import eis.iilang.ParameterList;
import eis.iilang.Percept;

public class Translator {

    public static Literal perceptToLiteral(Percept per) throws JasonException {
        Literal l = ASSyntax.createLiteral(per.getName());
        for (Parameter par : per.getParameters())
            l.addTerm(parameterToTerm(par));
        return l;
    }

    public static Action literalToAction(Literal action) throws NoValueException {
        Parameter[] pars = new Parameter[action.getArity()];
        for (int i = 0; i < action.getArity(); i++)
            pars[i] = termToParameter(action.getTerm(i));
        return new Action(action.getFunctor(), pars);
    }

    public static Parameter termToParameter(Term t) throws NoValueException {
        if (t.isNumeric()) {
            double d = ((NumberTerm) t).solve();
            if ((d == Math.floor(d)) && !Double.isInfinite(d))
                return new Numeral((int) d);
            return new Numeral(d);
        } else if (t.isList()) {
            ListTerm lt = (ListTerm) t;
            Parameter[] terms = new Parameter[lt.size()];
            for (int i = 0; i < lt.size(); i++)
                terms[i] = termToParameter(lt.get(i));
            return new ParameterList(terms);
        } else if (t.isString()) {
            return new Identifier(((StringTerm) t).getString());
        } else if (t.isLiteral()) {
            Literal l = (Literal) t;
            if (l.getArity() == 0)
                return new Identifier(l.getFunctor());
            Parameter[] terms = new Parameter[l.getArity()];
            for (int i = 0; i < l.getArity(); i++)
                terms[i] = termToParameter(l.getTerm(i));
            return new Function(l.getFunctor(), terms);
        } else {
            return new Identifier(t.toString());
        }
    }

    public static Term parameterToTerm(Parameter par) {
        if (par instanceof Numeral) {
            double d = ((Numeral) par).getValue().doubleValue();
            if (d == Math.floor(d) && !Double.isInfinite(d))
                return ASSyntax.createNumber((int) d);
            return ASSyntax.createNumber(d);
        } else if (par instanceof Identifier) {
            try {
                return ASSyntax.parseTerm(((Identifier) par).getValue());
            } catch (ParseException e) {
                return ASSyntax.createString(((Identifier) par).getValue());
            }
        } else if (par instanceof Function) {
            Function f = (Function) par;
            Literal l = ASSyntax.createLiteral(f.getName());
            for (Parameter p : f.getParameters())
                l.addTerm(parameterToTerm(p));
            return l;
        } else if (par instanceof ParameterList) {
            ListTerm list = new ListTermImpl();
            for (Parameter p : (ParameterList) par)
                list.add(parameterToTerm(p));
            return list;
        }
        return ASSyntax.createString(par.toString());
    }

    public static Term[] parametersToTerms(List<Parameter> list) {
        Term[] ret = new Term[list.size()];
        for (int i = 0; i < list.size(); i++)
            ret[i] = parameterToTerm(list.get(i));
        return ret;
    }
}
