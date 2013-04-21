package controllers;

import java.util.Date;
import java.util.List;

import models.Quote;
import models.Quote.QuoteState;
import models.QuotesListContent;
import models.User;

import org.bson.types.ObjectId;

import org.codehaus.jackson.node.ObjectNode;
import play.Configuration;
import play.Logger;
import play.Play;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Security.Authenticated;
import scala.util.parsing.json.JSON;
import utils.DBHolder;
import views.html.loginForm;
import views.html.quotes_list;

import com.google.code.morphia.Key;
import com.google.code.morphia.query.UpdateOperations;

public class Admin extends Controller {

	public static Result login() {
		if (UserAuthenticator.isUserLoggedIn()) {
			return redirect(controllers.routes.Admin.showNewlyAddedQuotes());
		}
		return ok(loginForm.render());
	}

	public static Result authenticate() {
        final User formUser = form(User.class).bindFromRequest().get();
        final User user = User.findByName(formUser.getUsername());

        if (user == null) {
        	flash("error", "Zadali jste špatné uživatelské jméno");
        } else {
        	if (formUser.getPassword().equals(user.getPassword())) {
                session("userId", user.getId().toString());
                return redirect(controllers.routes.Admin.showNewlyAddedQuotes());
            } else {
            	flash("error", "Zadali jste špatné heslo");
            }
        }
        return redirect(controllers.routes.Admin.login());
    }

	public static Result logout() {
		session().clear();
		return redirect(controllers.routes.Application.showApprovedQuotes());
	}

	@Authenticated(UserAuthenticator.class)
	public static Result reject() {
		String id = request().body().asFormUrlEncoded().get("id")[0];

        Quote oldQuote = Quote.findById(new ObjectId(id));

		Quote.delete(new ObjectId(id), false);

        return redirectByQuoteState(oldQuote.quoteState);
	}

	@Authenticated(UserAuthenticator.class)
	public static Result setChecked() {
		String id = request().body().asFormUrlEncoded().get("id")[0];

		Quote.setChecked(new ObjectId(id));

		return redirect(controllers.routes.Admin.showApprovedQuotes());
	}

	@Authenticated(UserAuthenticator.class)
    public static Result updateQuote() {

        Quote quote = form(Quote.class).bindFromRequest().get();

        final UpdateOperations<Quote> updateOperations =
                DBHolder.ds.createUpdateOperations(Quote.class)
                        .set("quoteText", quote.quoteText)
                        .set("demagogBacklinkUrl", quote.demagogBacklinkUrl)
                        .set("author", quote.author)
                        .set("url", quote.url)
                        .set("lastUpdateDate", new Date());

        Quote oldQuote = Quote.findById(quote.id);

        if (request().body().asFormUrlEncoded().containsKey("published") && oldQuote.quoteState != QuoteState.CHECKED_AND_PUBLISHED) {
        	updateOperations.set("quoteState", QuoteState.CHECKED_AND_PUBLISHED).set("publishedDate", new Date());
        }
        if (request().body().asFormUrlEncoded().containsKey("approved") && oldQuote.quoteState == QuoteState.NEW) {
        	updateOperations.set("quoteState", QuoteState.APPROVED_FOR_VOTING).set("approvalDate", new Date());
        }

        DBHolder.ds.update(new Key<Quote>(Quote.class, quote.id), updateOperations);

        return redirectByQuoteState(oldQuote.quoteState);
    }

	private static Result redirectByQuoteState(QuoteState quoteState) {
        switch (quoteState) {
			case NEW:
		        return redirect(routes.Admin.showNewlyAddedQuotes());
			case APPROVED_FOR_VOTING:
		        return redirect(routes.Admin.showApprovedQuotes());
			case ANALYSIS_IN_PROGRESS:
		        return redirect(routes.Admin.showQuotesInAnalysis());
			case CHECKED_AND_PUBLISHED:
		        return redirect(routes.Admin.showPublishedQuotes());
		    default:
		    	throw new IllegalArgumentException();
        }
	}

	@Authenticated(UserAuthenticator.class)
	public static Result showQuotes(QuoteState state) {
		List<Quote> quotes = Quote.findAllWithStateOrderedByCreationDate(state);

        // FIXME Michal Bernhard 26.03 : when instead of third parameter 'QuotesListContent.CHECKED' is null it
        // doesn't work, dunnno why
		return ok(quotes_list.render(quotes, true, QuotesListContent.CHECKED, null, null, null));
	}

	@Authenticated(UserAuthenticator.class)
	public static Result showNewlyAddedQuotes() {
		return showQuotes(QuoteState.NEW);
	}

	@Authenticated(UserAuthenticator.class)
	public static Result showApprovedQuotes() {
		return showQuotes(QuoteState.APPROVED_FOR_VOTING);
	}

	@Authenticated(UserAuthenticator.class)
	public static Result showQuotesInAnalysis() {
		return showQuotes(QuoteState.ANALYSIS_IN_PROGRESS);
	}

	@Authenticated(UserAuthenticator.class)
	public static Result showPublishedQuotes() {
		return showQuotes(QuoteState.CHECKED_AND_PUBLISHED);
	}

    @Authenticated(UserAuthenticator.class)
    public static Result showSettings() {
        Configuration configuration = Play.application().configuration();

        StringBuilder sb = new StringBuilder();

        for(String key : configuration.keys()) {
            String keyValue = "n/a";
            try {
                keyValue = configuration.getString(key);
            } catch (Exception /*com.typesafe.config.ConfigException*/ ex) {
                keyValue = "Cannot obtain string value of configuration key because of: " + ex.getMessage();
            }

            sb.append(key).append(" : ").append(keyValue).append("\n\n");
        }

        return ok(sb.toString());
    }

}
