
import scala.concurrent.duration._

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import io.gatling.jdbc.Predef._

import scala.util.Random

class demostore extends Simulation {


	val domain = "demostore.gatling.io"

	val httpProtocol = http
		.baseUrl("http://" + domain)

	val categoryFeeder = csv("data/categoryDetails.csv").random	
	val jsonFeederProducts = jsonFile("data/productDetails.json").random
	val loginCsvFeeder = csv("data/loginDetails.csv").circular

	val rnd = new Random()
	def randomString(length: Int): String = {
		rnd.alphanumeric.filter(_.isLetter).take(length).mkString
	}

	val initSession = exec(flushCookieJar)
						.exec(session => session.set("randomnumber", rnd.nextInt()))
						.exec(session => session.set("customerLoggedin", false))
						.exec(session => session.set("cartTotal", 0.00))
						.exec(addCookie(Cookie("sessionId", randomString(10)).withDomain(domain)))


	object Cmspages{
		def homepage = {
			exec(http("Load Home Page")
			.get("/")
			.check(status.is(200))
			.check(regex("<title>Gatling Demo-Store</title>").exists)
			.check(css("#_csrf","content").saveAs("csrfValue")))
		}

		def aboutUs = {
			exec(http("Load About Us Page")
			.get("/about-us")
			.check(status.is(200))
			.check(substring("About Us")))
		}
	}

	object Catalog{
		object Category{
			def view = {
				feed(categoryFeeder)
				.exec(http("Load category Page - ${categoryName}")
					.get("/category/${categorySlug}")
					.check(status.is(200))
					.check(css("#CategoryName").is("${categoryName}"))
			)
			}
		}

		object Product{
			def view = {
				feed(jsonFeederProducts)
				.exec(http("Load Product page - ${name}")
					.get("/product/${slug}")
					.check(status.is(200))
					.check(css("#ProductDescription").is("${description}")))
			}

			def add = {
				exec(view)
				.exec(http("Add product to cart")
				.get("/cart/add/${id}")
				.check(status.is(200))
				.check(substring("items in your cart"))
				)
				.exec(session =>{
					val currentCartTotal = session("cartTotal").as[Double]
					val itemPrice = session("price").as[Double]
					session.set("cartTotal", (currentCartTotal + itemPrice))
				})
			}
		}
	}

	object Customer{
		def login ={
			feed(loginCsvFeeder)
			.exec(
				http("Login User")
				.get("/login")
				.check(status.is(200))
				.check(substring("Username:"))
			)
			.exec(
				http("Customer Login action")
				.post("/login")
				.formParam("_csrf", "${csrfValue}")
				.formParam("username", "${username}")
				.formParam("password", "${password}")
				.check(status.is(200))
			)
			.exec(session => session.set("customerLoggedin", true))

		}
	}



	object Checkout{
			def viewCart = {
				doIf(session => !session("customerLoggedin").as[Boolean]){
					exec(Customer.login)
				}
				.exec(
					http("Load Cart Page")
					.get("/cart/view")
					.check(status.is(200))
					.check(css("#grandTotal").is("$$${cartTotal}")))
			}

			def checkOut = {
				exec(
					http("Checkout cart")
					.get("/cart/checkout")
					.check(status.is(200))
					.check(substring("Thanks for your order! See you soon!")))
			}
		}


	val scn = scenario("demostore")
		.exec(initSession)
		.exec(Cmspages.homepage)
		.pause(2)
		.exec(Cmspages.aboutUs)
		.pause(2)
		.exec(Catalog.Category.view)
		.pause(2)
		.exec(Catalog.Product.add)
		.pause(2)
		.exec(Checkout.viewCart)
		.pause(2)
		.exec(Checkout.checkOut)

	setUp(
		scn.inject(atOnceUsers(3),
		nothingFor(5.seconds),
		rampUsers(10) during (20.seconds),
		nothingFor(10.seconds),
		constantUsersPerSec(1) during(20.seconds),
		).protocols(httpProtocol))
}




