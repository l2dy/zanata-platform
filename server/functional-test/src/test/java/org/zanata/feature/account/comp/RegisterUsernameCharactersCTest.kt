/*
 * Copyright 2017, Red Hat, Inc. and individual contributors as indicated by the
 * @author tags. See the copyright.txt file in the distribution for a full
 * listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this software; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA, or see the FSF
 * site: http://www.fsf.org.
 */
package org.zanata.feature.account.comp

import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.zanata.feature.testharness.TestPlan
import org.zanata.feature.testharness.ZanataTestCase
import org.zanata.page.account.RegisterPage
import org.zanata.workflow.BasicWorkFlow

import org.assertj.core.api.Assertions.assertThat

/**
 * Test available characters to ensure only the specified set is allowed
 *
 * @author Damian Jansen [djansen@redhat.com](mailto:djansen@redhat.com)
 */
@Category(TestPlan.ComprehensiveTest::class)
@RunWith(Parameterized::class)
class RegisterUsernameCharactersCTest(private val testCharacter: String)
    : ZanataTestCase() {

    companion object {
        private val log = org.slf4j.LoggerFactory
                .getLogger(RegisterUsernameCharactersCTest::class.java)

        @JvmStatic
        @Parameterized.Parameters
        fun data(): Collection<String> {
            val characters = ArrayList<String>()
            for (i in 32..126) {
                val character = Character.toString(i.toChar())
                if (!character.matches("[a-z]|[0-9]|_".toRegex())) {
                    characters.add(character)
                }
            }
            return characters
        }
    }

    @Test(timeout = MAX_SHORT_TEST_DURATION.toLong())
    fun usernameCharacters() {
        log.info("")
        val registerPage = BasicWorkFlow()
                .goToPage("account/register", RegisterPage::class.java)
                .enterUserName("test" + this.testCharacter)
        registerPage.defocus()
        registerPage.slightPause()
        assertThat(registerPage.errors)
                .contains(RegisterPage.USERNAME_VALIDATION_ERROR)
    }
}
