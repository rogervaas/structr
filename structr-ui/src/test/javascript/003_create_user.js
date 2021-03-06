/*
 * Copyright (C) 2010-2017 Structr GmbH
 *
 * This file is part of Structr <http://structr.org>.
 *
 * Structr is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Structr is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Structr.  If not, see <http://www.gnu.org/licenses/>.
 */
var s = require('../setup'),
	login = require('../templates/login'),
	createPage = require('../templates/createPage');

var testName = '003_create_user';
var heading = "Create User", sections = [];
var desc = "This animation shows how to create a new user.";
var numberOfTests = 3;

s.startRecording(window, casper, testName);

casper.test.begin(testName, numberOfTests, function(test) {

	casper.start(s.url);

	login.init(test, 'admin', 'admin');

	sections.push('Click on the "Users and Groups" menu entry.');

	casper.then(function() {
		s.moveMousePointerAndClick(casper, {selector: "#security_", wait: 1000});
	});

	casper.then(function() {
		s.moveMousePointerAndClick(casper, {selector: "#usersAndGroups_", wait: 1000});
	});

	sections.push('Click the "Add User" icon.');

	casper.then(function() {
		s.moveMousePointerAndClick(casper, {selector: ".add_user_icon", wait: 1000});
	});

	casper.then(function() {
		test.assertElementCount('#users .node.user', 2);
	});

	sections.push('A new user with a random name has been created in the users area.');

	casper.then(function() {
		s.animateHtml(testName, heading, sections);
	});

	casper.run();

});